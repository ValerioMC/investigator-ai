# Graph Schema — InvestigatorAI

Full catalogue of Neo4j nodes and relationships.

## Nodes

### Person
Represents a natural person of investigative interest.
```
Person {
  id:            String  (UUID, required)
  fullName:      String  (required)
  birthDate:     Date?
  nationality:   String? (ISO 3166-1 alpha-2)
  taxCode:       String? (codice fiscale, hashed at rest)
  politicalRole: String? (last known role, denormalized for quick filter)
  riskScore:     Float?  (computed, 0.0–1.0)
}
```

### Company
Represents a legal entity (Srl, SpA, Ltd, SA, trust, foundation, etc.)
```
Company {
  id:                 String  (UUID, required)
  name:               String  (required)
  registrationNumber: String?
  jurisdiction:       String  (ISO 3166-1 alpha-2)
  legalForm:          String  (Srl, SpA, Ltd, SA, Trust, Foundation, …)
  active:             Boolean
  vatNumber:          String?
  registeredAddress:  String?
  sector:             String? (NACE code)
}
```

### BankAccount
```
BankAccount {
  id:          String
  institution: String
  jurisdiction: String
  currency:    String (ISO 4217)
  iban:        String? (masked: IT60 **** **** **** **** 00)
  swift:       String?
}
```

### Document
Represents a source document ingested into Qdrant and referenced in the graph.
```
Document {
  id:           String
  title:        String
  sourceType:   String  (NEWS_ARTICLE | COURT_RECORD | COMPANY_FILING |
                         PARLIAMENTARY_ACT | LEAKED_DOCUMENT | OFFICIAL_REGISTRY)
  sourceUrl:    String?
  publishedAt:  Date?
  reliability:  String  (HIGH | MEDIUM | LOW | UNVERIFIED)
  language:     String  (ISO 639-1)
  qdrantIds:    String[] (chunk IDs in Qdrant)
}
```

### Crime
```
Crime {
  id:          String
  type:        String  (CORRUPTION | FRAUD | MONEY_LAUNDERING | TAX_EVASION |
                        BRIBERY | EMBEZZLEMENT | OTHER)
  description: String
  severity:    String  (FELONY | MISDEMEANOR | INVESTIGATION_ONLY)
  status:      String  (CONVICTED | ACQUITTED | PENDING | ARCHIVED)
}
```

### Contract
Public procurement contract.
```
Contract {
  id:             String
  title:          String
  amount:         Float   (EUR)
  awardedAt:      Date
  cpvCode:        String? (EU procurement classification)
  publicBodyName: String
  source:         String  (ANAC | TED_EU | LOCAL_REGISTRY)
  sourceUrl:      String?
}
```

### PublicBody
Government entity, municipality, state-owned company, etc.
```
PublicBody {
  id:           String
  name:         String
  level:        String  (NATIONAL | REGIONAL | MUNICIPAL | EU | OTHER)
  country:      String
}
```

### Jurisdiction
```
Jurisdiction {
  id:       String
  name:     String
  isoCode:  String  (ISO 3166-1 alpha-2)
  taxHaven: Boolean (FATF / OECD classification)
  euMember: Boolean
}
```

### Transaction
Financial transaction between two accounts.
```
Transaction {
  id:          String
  amount:      Float
  currency:    String
  date:        Date
  description: String?
  suspicious:  Boolean  (flagged by FinancialFlowAgent)
}
```

---

## Relationships

### Ownership & Control
```
(Person)-[:OWNS {
  sharePercent: Float,
  since: Date?,
  until: Date?,
  directOrIndirect: String  (DIRECT | INDIRECT)
}]->(Company)

(Company)-[:CONTROLS {
  mechanism: String,  (MAJORITY_SHARE | BOARD_CONTROL | TRUST | NOMINEE)
  sharePercent: Float?
}]->(Company)

(Person)-[:IS_BENEFICIAL_OWNER_OF {
  confirmedBy: String  (REGISTRY | LEAK | INVESTIGATION)
}]->(Company)
```

### Roles
```
(Person)-[:IS_DIRECTOR_OF {
  role: String,  (CEO | CFO | CHAIRMAN | BOARD_MEMBER | LEGAL_REP | NOMINEE)
  from: Date,
  to: Date?      (null = currently active)
}]->(Company)

(Person)-[:HELD_PUBLIC_ROLE {
  title:  String,
  body:   String,
  from:   Date,
  to:     Date?
}]->(PublicBody)
```

### Personal relations
```
(Person)-[:FAMILY_RELATION {
  type: String  (SPOUSE | CHILD | PARENT | SIBLING | PARTNER)
}]->(Person)

(Person)-[:ASSOCIATED_WITH {
  context: String,
  strength: String  (STRONG | WEAK | ALLEGED)
}]->(Person)
```

### Legal
```
(Person)-[:CONVICTED_OF {
  year:  Int,
  court: String,
  sentence: String?
}]->(Crime)

(Company)-[:INVESTIGATED_FOR {
  year: Int,
  body: String,
  outcome: String  (PENDING | ARCHIVED | CONVICTED | ACQUITTED)
}]->(Crime)
```

### Contracts & Procurement
```
(Contract)-[:AWARDED_TO]->(Company)
(PublicBody)-[:ISSUED]->(Contract)

(Person)-[:SIGNED_CONTRACT {
  role: String
}]->(Contract)
```

### Financial flows
```
(Transaction)-[:FROM]->(BankAccount)
(Transaction)-[:TO]->(BankAccount)
(Person)-[:OWNS_ACCOUNT]->(BankAccount)
(Company)-[:OWNS_ACCOUNT]->(BankAccount)
```

### Document links
```
(Person)-[:MENTIONED_IN {context: String}]->(Document)
(Company)-[:MENTIONED_IN {context: String}]->(Document)
(Contract)-[:SOURCED_FROM]->(Document)
(Crime)-[:DOCUMENTED_IN]->(Document)
```

### Geographic
```
(Company)-[:REGISTERED_IN]->(Jurisdiction)
(BankAccount)-[:HELD_IN]->(Jurisdiction)
```

---

## Key Cypher patterns used by agents

### Find UBO (Ultimate Beneficial Owner) up to 5 hops
```cypher
MATCH path = (p:Person)-[:OWNS|CONTROLS*1..5]->(c:Company {name: $companyName})
RETURN p, length(path) as distance, path
ORDER BY distance
```

### Find all companies controlled by a person (direct + indirect)
```cypher
MATCH (p:Person {fullName: $name})-[:OWNS|IS_DIRECTOR_OF|CONTROLS*1..4]->(c:Company)
RETURN DISTINCT c
```

### Detect potential conflict of interest
```cypher
MATCH (p:Person)-[:HELD_PUBLIC_ROLE]->(pb:PublicBody)-[:ISSUED]->(ct:Contract)
      -[:AWARDED_TO]->(co:Company)<-[:OWNS|IS_DIRECTOR_OF]-(p)
WHERE ct.awardedAt >= date($from) AND ct.awardedAt <= date($to)
RETURN p, pb, ct, co
```

### Find connections through tax havens
```cypher
MATCH (p:Person)-[:OWNS|CONTROLS*1..3]->(c:Company)
      -[:REGISTERED_IN]->(j:Jurisdiction {taxHaven: true})
RETURN p, c, j
```

### Shortest path between two entities
```cypher
MATCH path = shortestPath(
  (a:Person {fullName: $person1})-[*..6]-(b:Person {fullName: $person2})
)
RETURN path
```
