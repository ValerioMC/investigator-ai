# Data Sources — InvestigatorAI

Public data sources used for ingestion. No personal data beyond what is
publicly available in official registries.

## Structured sources → Neo4j

| Source | What it provides | Format | URL |
|---|---|---|---|
| OpenCorporates | Company registrations worldwide | REST API / CSV | opencorporates.com |
| ANAC (Italy) | Public procurement contracts | REST API | dati.anticorruzione.it |
| TED EU | EU public procurement | REST API | ted.europa.eu |
| Registro Imprese CCIAA | Italian company registry | REST / scrape | impresainungiorno.gov.it |
| OpenSanctions | Sanctioned persons & entities | JSON download | opensanctions.org |
| ICIJ Offshore Leaks DB | Panama/Pandora Papers entities | CSV download | offshoreleaks.icij.org |
| Arachne (EU) | EU funds beneficiaries | REST API | ec.europa.eu/sante/arachne |

## Unstructured sources → Qdrant

| Source | What it provides | Format | Collection |
|---|---|---|---|
| ParlamentoIT Open Data | Parliamentary acts, votes | XML / PDF | `parliamentary_acts` |
| Giustizia.it | Public court records | PDF / HTML | `court_records` |
| EUR-Lex | EU legislation and rulings | XML | `legislation` |
| GDELT | Global news event database | CSV | `news_articles` |
| CommonCrawl (filtered) | Web news archives | WARC | `news_articles` |

## Demo fixture data

For development and article demos, use the fictional dataset in
`investigator-domain/src/test/resources/fixtures/`:

- `fictional-persons.json` — 15 fictional Italian names
- `fictional-companies.json` — 10 fictional companies across IT, LU, BVI
- `fictional-contracts.json` — 4 fictional public contracts
- `fictional-documents.txt` — news article excerpts (fictional)

**Never use real person names in fixtures.**
