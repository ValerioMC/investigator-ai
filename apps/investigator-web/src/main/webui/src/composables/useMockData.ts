import type {
  InvestigationReport,
  InvestigationSession,
  Person,
  Company,
  Contract,
  GraphData,
} from '@/types/api'

export function useMockData() {
  const mockPersons: Person[] = [
    {
      id: 'p-001',
      fullName: 'Marco Ferretti',
      birthDate: '1968-03-14',
      nationality: 'IT',
      politicalRole: 'Consigliere Comunale, Brescia',
      riskScore: 0.82,
      relatedCompanies: ['c-001', 'c-002'],
      relatedContracts: ['k-001', 'k-002'],
      documents: ['doc-001', 'doc-002'],
    },
    {
      id: 'p-002',
      fullName: 'Luigi Conti',
      birthDate: '1971-09-22',
      nationality: 'IT',
      politicalRole: 'Sindaco, Comune di Brescia (2016-2024)',
      riskScore: 0.75,
      relatedCompanies: ['c-001', 'c-003'],
      relatedContracts: ['k-001'],
      documents: ['doc-001', 'doc-003'],
    },
    {
      id: 'p-003',
      fullName: 'Mario Conti',
      birthDate: '1975-06-08',
      nationality: 'IT',
      politicalRole: undefined,
      riskScore: 0.65,
      relatedCompanies: ['c-002', 'c-003'],
      relatedContracts: ['k-002'],
      documents: ['doc-002'],
    },
  ]

  const mockCompanies: Company[] = [
    {
      id: 'c-001',
      name: 'Costruzioni Ferretti Srl',
      registrationNumber: 'IT03847291006',
      jurisdiction: 'IT',
      legalForm: 'Srl',
      active: true,
      taxHaven: false,
      ultimateBeneficialOwners: ['Marco Ferretti'],
      relatedPersons: ['p-001', 'p-002'],
      contracts: ['k-001', 'k-002'],
    },
    {
      id: 'c-002',
      name: 'LuxHold SA',
      registrationNumber: 'LU20183847',
      jurisdiction: 'LU',
      legalForm: 'SA',
      active: true,
      taxHaven: true,
      ultimateBeneficialOwners: ['Marco Ferretti'],
      relatedPersons: ['p-001', 'p-003'],
      contracts: [],
    },
    {
      id: 'c-003',
      name: 'Esposito Offshore Ltd',
      registrationNumber: 'LU20195521',
      jurisdiction: 'LU',
      legalForm: 'Ltd',
      active: false,
      taxHaven: true,
      ultimateBeneficialOwners: ['Mario Conti'],
      relatedPersons: ['p-002', 'p-003'],
      contracts: [],
    },
  ]

  const mockContracts: Contract[] = [
    {
      id: 'k-001',
      title: 'Riqualificazione Piazza Loggia — Fase II',
      amount: 1_200_000,
      awardedAt: '2022-04-15',
      publicBodyName: 'Comune di Brescia',
      awardedTo: 'Costruzioni Ferretti Srl',
      suspicionScore: 0.88,
    },
    {
      id: 'k-002',
      title: 'Manutenzione straordinaria rete stradale urbana',
      amount: 450_000,
      awardedAt: '2023-01-20',
      publicBodyName: 'Comune di Brescia',
      awardedTo: 'Costruzioni Ferretti Srl',
      suspicionScore: 0.71,
    },
  ]

  const mockInvestigationReport: InvestigationReport = {
    query:
      'Chi controlla realmente Costruzioni Ferretti Srl e ci sono conflitti di interesse nei loro appalti pubblici?',
    summary:
      'L\'indagine ha identificato una rete di controllo societario che collega Marco Ferretti, consigliere comunale di Brescia, a due aggiudicazioni di appalti pubblici per un totale di €1,65 milioni. Luigi Conti, sindaco durante il periodo delle aggiudicazioni, risulta avere legami familiari con soci della holding lussemburghese LuxHold SA. Non è stata presentata alcuna dichiarazione di conflitto di interessi.',
    findings: [
      {
        claim:
          'Marco Ferretti è il beneficiario effettivo al 100% di Costruzioni Ferretti Srl attraverso la holding intermedia LuxHold SA con sede in Lussemburgo.',
        confidence: 'HIGH',
        evidence: [
          'GraphPath: Ferretti->OWNS(100%)->LuxHold SA',
          'GraphPath: LuxHold SA->CONTROLS->Costruzioni Ferretti Srl',
          'Document#doc-001: Registro imprese LU, estratto 2023-11-14',
          'Document#doc-002: CCIAA Brescia, visura ordinaria 2024-02-01',
        ],
        agentSource: 'CorporateAgent',
      },
      {
        claim:
          'Luigi Conti ha votato favorevolmente all\'aggiudicazione del contratto "Riqualificazione Piazza Loggia" senza dichiarare il conflitto di interessi derivante dal rapporto di primo grado con Mario Conti, socio di LuxHold SA.',
        confidence: 'HIGH',
        evidence: [
          'GraphPath: Luigi Conti->FAMILY_RELATION(fratello)->Mario Conti',
          'GraphPath: Mario Conti->OWNS(15%)->LuxHold SA',
          'Document#doc-003: Verbale consiglio comunale Brescia, seduta 2022-04-12',
          'GraphPath: Luigi Conti->HELD_PUBLIC_ROLE(Sindaco)->Comune di Brescia',
        ],
        agentSource: 'PersonProfileAgent',
      },
      {
        claim:
          'I margini operativi di Costruzioni Ferretti Srl mostrano un\'anomalia del +340% negli esercizi corrispondenti alle aggiudicazioni pubbliche rispetto alla media di settore.',
        confidence: 'MEDIUM',
        evidence: [
          'FinancialAnalysis: EBITDA margin 2022=41.2%, media settore=9.3%',
          'FinancialAnalysis: EBITDA margin 2023=38.7%, media settore=9.8%',
          'Document#doc-004: Bilancio Ferretti Srl 2022, deposito CCIAA',
        ],
        agentSource: 'FinancialFlowAgent',
      },
      {
        claim:
          'Esposito Offshore Ltd risulta inattiva dal 2021 ma mantiene partecipazioni formali in LuxHold SA, con un possibile utilizzo come veicolo di occultamento della proprietà effettiva.',
        confidence: 'MEDIUM',
        evidence: [
          'GraphPath: Esposito Offshore->OWNS(8%)->LuxHold SA',
          'Document#doc-005: Registro commerciale LU, stato inattivo confermato',
        ],
        agentSource: 'CorporateAgent',
      },
    ],
    entityMap: {
      persons: ['Marco Ferretti', 'Luigi Conti', 'Mario Conti'],
      companies: ['Costruzioni Ferretti Srl', 'LuxHold SA', 'Esposito Offshore Ltd'],
      contracts: ['Riqualificazione Piazza Loggia — Fase II', 'Manutenzione straordinaria rete stradale urbana'],
    },
    recommendedFollowUps: [
      "Verificare le dichiarazioni patrimoniali di Luigi Conti per gli anni 2018–2024 presso la Prefettura di Brescia",
      "Richiedere documenti di beneficiario effettivo aggiornati al registro commerciale lussemburghese",
      "Analizzare i flussi bancari tra LuxHold SA e Costruzioni Ferretti Srl negli esercizi 2020–2023",
      "Verificare se altri consiglieri comunali hanno votato su appalti con connessioni societarie simili",
    ],
    disclaimer:
      'Questo rapporto è uno strumento di supporto al giornalismo investigativo. Tutte le affermazioni richiedono verifica editoriale indipendente prima della pubblicazione. I dati provengono da fonti pubbliche e possono essere incompleti.',
  }

  const mockSessions: InvestigationSession[] = [
    {
      id: 'sess-001',
      query:
        'Chi controlla realmente Costruzioni Ferretti Srl e ci sono conflitti di interesse nei loro appalti pubblici?',
      createdAt: new Date(Date.now() - 2 * 60 * 60 * 1000).toISOString(),
      status: 'COMPLETED',
      findingCount: 4,
      confidenceDistribution: { high: 2, medium: 2, low: 0, unverified: 0 },
      report: mockInvestigationReport,
      workspace: 'Inchiesta Appalti Brescia',
    },
    {
      id: 'sess-002',
      query:
        "Quali sono i legami tra la famiglia Conti e le società di costruzione aggiudicatarie di appalti nel bresciano 2019-2023?",
      createdAt: new Date(Date.now() - 3 * 24 * 60 * 60 * 1000).toISOString(),
      status: 'COMPLETED',
      findingCount: 6,
      confidenceDistribution: { high: 3, medium: 2, low: 1, unverified: 0 },
      report: null,
      workspace: 'Inchiesta Appalti Brescia',
    },
    {
      id: 'sess-003',
      query: "Analisi flussi finanziari LuxHold SA e società collegate 2020-2023",
      createdAt: new Date(Date.now() - 7 * 24 * 60 * 60 * 1000).toISOString(),
      status: 'FAILED',
      findingCount: 0,
      confidenceDistribution: { high: 0, medium: 0, low: 0, unverified: 0 },
      report: null,
      workspace: 'Inchiesta Appalti Brescia',
    },
  ]

  const mockGraphData: GraphData = {
    nodes: [
      {
        id: 'p-001',
        label: 'Marco Ferretti',
        type: 'Person',
        riskScore: 0.82,
        properties: {
          fullName: 'Marco Ferretti',
          nationality: 'IT',
          politicalRole: 'Consigliere Comunale',
          riskScore: 0.82,
        },
      },
      {
        id: 'p-002',
        label: 'Luigi Conti',
        type: 'Person',
        riskScore: 0.75,
        properties: {
          fullName: 'Luigi Conti',
          nationality: 'IT',
          politicalRole: 'Sindaco, Brescia',
          riskScore: 0.75,
        },
      },
      {
        id: 'p-003',
        label: 'Mario Conti',
        type: 'Person',
        riskScore: 0.65,
        properties: {
          fullName: 'Mario Conti',
          nationality: 'IT',
          riskScore: 0.65,
        },
      },
      {
        id: 'c-001',
        label: 'Costruzioni Ferretti Srl',
        type: 'Company',
        jurisdiction: 'IT',
        properties: {
          name: 'Costruzioni Ferretti Srl',
          jurisdiction: 'IT',
          active: true,
          registrationNumber: 'IT03847291006',
        },
      },
      {
        id: 'c-002',
        label: 'LuxHold SA',
        type: 'Company',
        jurisdiction: 'LU',
        properties: {
          name: 'LuxHold SA',
          jurisdiction: 'LU',
          active: true,
          taxHaven: true,
          registrationNumber: 'LU20183847',
        },
      },
      {
        id: 'c-003',
        label: 'Esposito Offshore Ltd',
        type: 'Company',
        jurisdiction: 'LU',
        properties: {
          name: 'Esposito Offshore Ltd',
          jurisdiction: 'LU',
          active: false,
          taxHaven: true,
          registrationNumber: 'LU20195521',
        },
      },
      {
        id: 'k-001',
        label: 'Riqualificazione Piazza Loggia',
        type: 'Contract',
        properties: {
          title: 'Riqualificazione Piazza Loggia — Fase II',
          amount: 1200000,
          awardedAt: '2022-04-15',
          publicBodyName: 'Comune di Brescia',
        },
      },
      {
        id: 'k-002',
        label: 'Manutenzione strade',
        type: 'Contract',
        properties: {
          title: 'Manutenzione rete stradale urbana',
          amount: 450000,
          awardedAt: '2023-01-20',
          publicBodyName: 'Comune di Brescia',
        },
      },
      {
        id: 'pb-001',
        label: 'Comune di Brescia',
        type: 'PublicBody',
        properties: {
          name: 'Comune di Brescia',
          type: 'Municipalità',
          region: 'Lombardia',
        },
      },
    ],
    edges: [
      {
        id: 'e-001',
        source: 'p-001',
        target: 'c-002',
        relationshipType: 'OWNS',
        properties: { sharePercent: 77, since: '2015-06-01' },
      },
      {
        id: 'e-002',
        source: 'c-002',
        target: 'c-001',
        relationshipType: 'CONTROLS',
        properties: { mechanism: 'ownership_chain', sharePercent: 100 },
      },
      {
        id: 'e-003',
        source: 'p-003',
        target: 'c-002',
        relationshipType: 'OWNS',
        properties: { sharePercent: 15, since: '2018-03-10' },
      },
      {
        id: 'e-004',
        source: 'c-003',
        target: 'c-002',
        relationshipType: 'OWNS',
        properties: { sharePercent: 8 },
      },
      {
        id: 'e-005',
        source: 'p-002',
        target: 'p-003',
        relationshipType: 'FAMILY_RELATION',
        properties: { type: 'fratello' },
      },
      {
        id: 'e-006',
        source: 'p-002',
        target: 'pb-001',
        relationshipType: 'HELD_PUBLIC_ROLE',
        properties: { title: 'Sindaco', from: '2016-06-15', to: '2024-06-14' },
      },
      {
        id: 'e-007',
        source: 'pb-001',
        target: 'k-001',
        relationshipType: 'ISSUED',
        properties: {},
      },
      {
        id: 'e-008',
        source: 'pb-001',
        target: 'k-002',
        relationshipType: 'ISSUED',
        properties: {},
      },
      {
        id: 'e-009',
        source: 'k-001',
        target: 'c-001',
        relationshipType: 'AWARDED_TO',
        properties: { amount: 1200000 },
      },
      {
        id: 'e-010',
        source: 'k-002',
        target: 'c-001',
        relationshipType: 'AWARDED_TO',
        properties: { amount: 450000 },
      },
      {
        id: 'e-011',
        source: 'p-001',
        target: 'c-001',
        relationshipType: 'IS_DIRECTOR_OF',
        properties: { from: '2012-01-01' },
      },
    ],
  }

  return {
    mockPersons,
    mockCompanies,
    mockContracts,
    mockInvestigationReport,
    mockSessions,
    mockGraphData,
  }
}
