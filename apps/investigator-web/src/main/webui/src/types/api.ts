export type ConfidenceLevel = 'HIGH' | 'MEDIUM' | 'LOW' | 'UNVERIFIED'

export interface Finding {
  claim: string
  confidence: ConfidenceLevel
  evidence: string[]
  agentSource: string
}

export interface EntityMap {
  persons: string[]
  companies: string[]
  contracts: string[]
}

export interface InvestigationReport {
  query: string
  summary: string
  findings: Finding[]
  entityMap: EntityMap
  recommendedFollowUps: string[]
  disclaimer: string
}

export interface InvestigationSession {
  id: string
  query: string
  createdAt: string
  status: 'COMPLETED' | 'RUNNING' | 'FAILED'
  findingCount: number
  confidenceDistribution: {
    high: number
    medium: number
    low: number
    unverified: number
  }
  report: InvestigationReport | null
  workspace: string
}

export interface Person {
  id: string
  fullName: string
  birthDate?: string
  nationality?: string
  politicalRole?: string
  riskScore: number
  relatedCompanies: string[]
  relatedContracts: string[]
  documents: string[]
}

export interface Company {
  id: string
  name: string
  registrationNumber: string
  jurisdiction: string
  legalForm?: string
  active: boolean
  taxHaven: boolean
  ultimateBeneficialOwners: string[]
  relatedPersons: string[]
  contracts: string[]
}

export interface Contract {
  id: string
  title: string
  amount: number
  awardedAt: string
  publicBodyName: string
  awardedTo: string
  suspicionScore: number
}

export type NodeType = 'Person' | 'Company' | 'Contract' | 'PublicBody' | 'BankAccount' | 'Jurisdiction'

export interface GraphNode {
  id: string
  label: string
  type: NodeType
  properties: Record<string, string | number | boolean>
  riskScore?: number
  jurisdiction?: string
}

export interface GraphEdge {
  id: string
  source: string
  target: string
  relationshipType: string
  properties?: Record<string, string | number | boolean>
}

export interface GraphData {
  nodes: GraphNode[]
  edges: GraphEdge[]
}

export interface User {
  id: string
  email: string
  displayName: string
  initials: string
  role: 'JOURNALIST' | 'EDITOR' | 'ADMIN'
}

export interface Workspace {
  id: string
  name: string
  description?: string
  memberCount: number
}

export interface AuditEntry {
  id: string
  userId: string
  action: string
  entityType: string
  entityId: string
  timestamp: string
  metadata: Record<string, unknown>
}

export interface PaginatedResponse<T> {
  data: T[]
  total: number
  page: number
  pageSize: number
}
