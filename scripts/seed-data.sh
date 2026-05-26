#!/usr/bin/env bash
# Seed Neo4j with the Ferretti corruption investigation scenario.
# Requires: kubectl context pointing at the investigator-ai profile,
#           and the investigator-ai namespace with neo4j running.
set -euo pipefail

NAMESPACE="investigator-ai"
NEO4J_POD=$(kubectl get pod -n "$NAMESPACE" -l app=neo4j -o jsonpath='{.items[0].metadata.name}')

if [ -z "$NEO4J_POD" ]; then
  echo "ERROR: no neo4j pod found in namespace $NAMESPACE"
  echo "       Run 'make up' first."
  exit 1
fi

# Read password from the live secret so we don't hard-code it
NEO4J_PASS=$(kubectl get secret investigator-secrets -n "$NAMESPACE" \
  -o jsonpath='{.data.NEO4J_PASSWORD}' | base64 -d)

run_cypher() {
  kubectl exec -n "$NAMESPACE" "$NEO4J_POD" -- \
    cypher-shell -u neo4j -p "$NEO4J_PASS" --format plain "$@"
}

echo "==> Connected to pod: $NEO4J_POD"
echo "==> Clearing existing seed data (idempotent re-run)..."
run_cypher "
MATCH (n)
WHERE n.id IN [
  'p-001','p-002','p-003',
  'c-001','c-002','c-003',
  'k-001','k-002',
  'pb-001',
  'j-IT','j-LU',
  'd-001','d-002','d-003'
]
DETACH DELETE n
"

echo "==> Creating persons..."
run_cypher "
MERGE (p:Person {id: 'p-001'})
SET p.fullName    = 'Marco Ferretti',
    p.birthDate   = '1968-03-14',
    p.nationality = 'IT',
    p.politicalRole = 'Consigliere Comunale, Brescia',
    p.riskScore   = 0.82;

MERGE (p:Person {id: 'p-002'})
SET p.fullName    = 'Luigi Conti',
    p.birthDate   = '1971-09-22',
    p.nationality = 'IT',
    p.politicalRole = 'Sindaco, Comune di Brescia (2016-2024)',
    p.riskScore   = 0.75;

MERGE (p:Person {id: 'p-003'})
SET p.fullName    = 'Mario Conti',
    p.birthDate   = '1975-06-08',
    p.nationality = 'IT',
    p.riskScore   = 0.65;
"

echo "==> Creating companies..."
run_cypher "
MERGE (c:Company {id: 'c-001'})
SET c.name                 = 'Costruzioni Ferretti Srl',
    c.registrationNumber   = 'IT03847291006',
    c.jurisdiction         = 'IT',
    c.legalForm            = 'Srl',
    c.active               = true;

MERGE (c:Company {id: 'c-002'})
SET c.name                 = 'LuxHold SA',
    c.registrationNumber   = 'LU20183847',
    c.jurisdiction         = 'LU',
    c.legalForm            = 'SA',
    c.active               = true,
    c.taxHaven             = true;

MERGE (c:Company {id: 'c-003'})
SET c.name                 = 'Esposito Offshore Ltd',
    c.registrationNumber   = 'LU20195521',
    c.jurisdiction         = 'LU',
    c.legalForm            = 'Ltd',
    c.active               = false,
    c.taxHaven             = true;
"

echo "==> Creating contracts and public body..."
run_cypher "
MERGE (pb:PublicBody {id: 'pb-001'})
SET pb.name   = 'Comune di Brescia',
    pb.type   = 'Municipalità',
    pb.region = 'Lombardia';

MERGE (k:Contract {id: 'k-001'})
SET k.title          = 'Riqualificazione Piazza Loggia — Fase II',
    k.amount         = 1200000,
    k.awardedAt      = date('2022-04-15'),
    k.publicBodyName = 'Comune di Brescia',
    k.suspicionScore = 0.88;

MERGE (k:Contract {id: 'k-002'})
SET k.title          = 'Manutenzione straordinaria rete stradale urbana',
    k.amount         = 450000,
    k.awardedAt      = date('2023-01-20'),
    k.publicBodyName = 'Comune di Brescia',
    k.suspicionScore = 0.71;
"

echo "==> Creating jurisdictions..."
run_cypher "
MERGE (j:Jurisdiction {id: 'j-IT'})
SET j.name    = 'Italy',
    j.isoCode = 'IT',
    j.taxHaven = false;

MERGE (j:Jurisdiction {id: 'j-LU'})
SET j.name    = 'Luxembourg',
    j.isoCode = 'LU',
    j.taxHaven = true;
"

echo "==> Creating relationships..."
run_cypher "
MATCH (ferretti:Person {id: 'p-001'}), (luxhold:Company {id: 'c-002'})
MERGE (ferretti)-[:OWNS {sharePercent: 77, since: '2015-06-01'}]->(luxhold);

MATCH (luxhold:Company {id: 'c-002'}), (ferretti_srl:Company {id: 'c-001'})
MERGE (luxhold)-[:CONTROLS {mechanism: 'ownership_chain', sharePercent: 100}]->(ferretti_srl);

MATCH (mario:Person {id: 'p-003'}), (luxhold:Company {id: 'c-002'})
MERGE (mario)-[:OWNS {sharePercent: 15, since: '2018-03-10'}]->(luxhold);

MATCH (offshore:Company {id: 'c-003'}), (luxhold:Company {id: 'c-002'})
MERGE (offshore)-[:OWNS {sharePercent: 8}]->(luxhold);

MATCH (luigi:Person {id: 'p-002'}), (mario:Person {id: 'p-003'})
MERGE (luigi)-[:FAMILY_RELATION {type: 'fratello'}]->(mario);

MATCH (ferretti:Person {id: 'p-001'}), (srl:Company {id: 'c-001'})
MERGE (ferretti)-[:IS_DIRECTOR_OF {from: '2012-01-01'}]->(srl);

MATCH (luigi:Person {id: 'p-002'}), (pb:PublicBody {id: 'pb-001'})
MERGE (luigi)-[:HELD_PUBLIC_ROLE {title: 'Sindaco', from: '2016-06-15', to: '2024-06-14'}]->(pb);

MATCH (pb:PublicBody {id: 'pb-001'}), (k1:Contract {id: 'k-001'})
MERGE (pb)-[:ISSUED]->(k1);

MATCH (pb:PublicBody {id: 'pb-001'}), (k2:Contract {id: 'k-002'})
MERGE (pb)-[:ISSUED]->(k2);

MATCH (k1:Contract {id: 'k-001'}), (srl:Company {id: 'c-001'})
MERGE (k1)-[:AWARDED_TO {amount: 1200000}]->(srl);

MATCH (k2:Contract {id: 'k-002'}), (srl:Company {id: 'c-001'})
MERGE (k2)-[:AWARDED_TO {amount: 450000}]->(srl);

MATCH (srl:Company {id: 'c-001'}), (j:Jurisdiction {id: 'j-IT'})
MERGE (srl)-[:REGISTERED_IN]->(j);

MATCH (luxhold:Company {id: 'c-002'}), (j:Jurisdiction {id: 'j-LU'})
MERGE (luxhold)-[:REGISTERED_IN]->(j);

MATCH (offshore:Company {id: 'c-003'}), (j:Jurisdiction {id: 'j-LU'})
MERGE (offshore)-[:REGISTERED_IN]->(j);
"

echo "==> Creating documents and mentions..."
run_cypher "
MERGE (d:Document {id: 'd-001'})
SET d.title       = 'Brescia: appalto Piazza Loggia, dubbi sulla gara',
    d.sourceType  = 'news_article',
    d.sourceUrl   = 'https://example.org/news/brescia-piazza-loggia',
    d.publishedAt = date('2022-05-04'),
    d.reliability = 'MEDIUM';

MERGE (d:Document {id: 'd-002'})
SET d.title       = 'Indagine Procura Brescia: rete societaria Ferretti',
    d.sourceType  = 'court_record',
    d.sourceUrl   = 'https://example.org/court/brescia-ferretti-2023',
    d.publishedAt = date('2023-03-18'),
    d.reliability = 'HIGH';

MERGE (d:Document {id: 'd-003'})
SET d.title       = 'Dichiarazione redditi Sindaco Conti — anno 2022',
    d.sourceType  = 'official_filing',
    d.sourceUrl   = 'https://example.org/filings/conti-2022',
    d.publishedAt = date('2023-06-30'),
    d.reliability = 'HIGH';

MATCH (luigi:Person {id: 'p-002'}), (d1:Document {id: 'd-001'})
MERGE (luigi)-[:MENTIONED_IN {context: 'voto in Consiglio comunale su delibera appalti'}]->(d1);

MATCH (srl:Company {id: 'c-001'}), (d1:Document {id: 'd-001'})
MERGE (srl)-[:MENTIONED_IN {context: 'aggiudicataria appalto Piazza Loggia'}]->(d1);

MATCH (luigi:Person {id: 'p-002'}), (d2:Document {id: 'd-002'})
MERGE (luigi)-[:MENTIONED_IN {context: 'fascicolo aperto su conflitto di interessi'}]->(d2);

MATCH (mario:Person {id: 'p-003'}), (d2:Document {id: 'd-002'})
MERGE (mario)-[:MENTIONED_IN {context: 'socio occulto LuxHold SA'}]->(d2);

MATCH (srl:Company {id: 'c-001'}), (d2:Document {id: 'd-002'})
MERGE (srl)-[:MENTIONED_IN {context: 'destinataria appalti Comune di Brescia 2022-2023'}]->(d2);

MATCH (lux:Company {id: 'c-002'}), (d2:Document {id: 'd-002'})
MERGE (lux)-[:MENTIONED_IN {context: 'holding di controllo registrata in Lussemburgo'}]->(d2);

MATCH (luigi:Person {id: 'p-002'}), (d3:Document {id: 'd-003'})
MERGE (luigi)-[:MENTIONED_IN {context: 'mancata dichiarazione partecipazioni indirette del fratello'}]->(d3);
"

echo ""
echo "==> Seed complete. Graph summary:"
run_cypher "
MATCH (n) RETURN labels(n)[0] AS type, count(n) AS count
ORDER BY count DESC
"

echo ""
echo "Run 'make investigate' to execute the example query."
