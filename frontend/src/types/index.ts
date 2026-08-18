export interface Result<T = unknown> {
  code: number
  message: string
  data: T
}

export interface ChatTurn {
  role: 'user' | 'assistant'
  content: string
}

export interface ChunkSearchResult {
  docId: string
  docName: string
  section: string
  chunkIndex: number
  content: string
  score?: number
  distance?: number
}

export interface AgentStepEvent {
  step: number
  tool: string
  status: 'running' | 'done' | 'error'
  hits?: number
  summary?: string
  args?: Record<string, unknown>
}

export interface ChatSubmitResponse {
  taskId: string
}

export interface ChatTaskResult {
  status: string
  answer: string
  sources: ChunkSearchResult[]
  qaId?: number
  agentTrace?: AgentStepEvent[]
  retrievalMeta?: Record<string, unknown>
}

export interface ChatMessage {
  role: 'user' | 'ai'
  content: string
  status?: 'pending' | 'streaming' | 'completed' | 'failed'
  sources?: ChunkSearchResult[]
  agentSteps?: AgentStepEvent[]
  qaId?: number
  thinking?: string
  thinkOpen?: boolean
}

export interface FeedbackStatsVO {
  total: number
  positive: number
  negative: number
  positiveRate: number
  recentNegative: Array<{ qaId: number; question: string; rating: number; comment?: string; createdAt: string }>
}

export interface QaLogItem {
  id: number
  username: string
  question: string
  answer: string
  createdAt: string
}

export interface KbStatus {
  docCount: number
  lastSeedAt?: string
  seeded?: number
  skipped?: number
  files?: string[]
  error?: string
}
