// バックエンド API の DTO に対応する型定義。

export type RegistrationType = 'ONCE' | 'WEEKLY';
export type RegistrationStatus = 'ACTIVE' | 'COMPLETED' | 'EXPIRED';
export type DownloadStatus = 'SUCCESS' | 'FAILED' | 'EXPIRED';
export type BatchType = 'F4' | 'F2' | 'F4_F2';
export type BatchStatus = 'RUNNING' | 'SUCCESS' | 'PARTIAL_FAILURE' | 'FAILED';

export interface UserResponse {
  id: number;
  email: string;
  name: string | null;
  pictureUrl: string | null;
}

export interface LoginUrlResponse {
  authUrl: string;
  state: string;
}

export interface LoginResponse {
  accessToken: string;
  expiresIn: number;
  user: UserResponse;
}

export interface AreaResponse {
  id: string;
  name: string;
  sortOrder: number;
}

export interface StationResponse {
  id: string;
  areaId: string;
  name: string;
  asciiName: string | null;
  logoUrl: string | null;
  sortOrder: number;
}

export interface UserSettingsResponse {
  currentAreaId: string;
}

export interface UpdateSettingsResponse {
  currentAreaId: string;
  areaChangeFetchStatus: { batchExecutionId: number; status: BatchStatus };
}

export interface StationVisibilityResponse {
  stationId: string;
  visible: boolean;
}

export interface RegistrationRef {
  registrationId: number;
  type: RegistrationType;
}

export interface ProgramItem {
  id: number;
  title: string;
  performers: string | null;
  broadcastStartAt: string;
  broadcastEndAt: string;
  isPast: boolean;
  isWithinTimefreeWindow: boolean;
  registration: RegistrationRef | null;
}

export interface StationGroup {
  stationId: string;
  name: string;
  programs: ProgramItem[];
}

export interface ProgramListResponse {
  broadcastDate: string;
  stations: StationGroup[];
}

export interface ProgramDetailResponse extends ProgramItem {
  stationId: string;
  stationName: string;
  description: string | null;
  info: string | null;
  imageUrl: string | null;
}

export interface RegistrationResponse {
  id: number;
  stationId: string;
  title: string;
  broadcastStartAt: string;
  broadcastEndAt: string;
  dayOfWeek: number | null;
  registrationType: RegistrationType;
  status: RegistrationStatus;
}

export interface CreateRegistrationRequest {
  stationId: string;
  title: string;
  broadcastStartAt: string;
  broadcastEndAt: string;
  registrationType: RegistrationType;
}

export interface BadgeResponse {
  failedCount: number;
  expiredCount: number;
}

export interface HistoryItem {
  id: number;
  stationId: string;
  programTitle: string;
  performers: string | null;
  broadcastStartAt: string;
  broadcastEndAt: string;
  status: DownloadStatus;
  errorMessage: string | null;
  attemptedAt: string;
  fileDeletedAt: string | null;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface RecordingGroupResponse {
  title: string;
  count: number;
  latestBroadcastAt: string;
}

export interface RecordingResponse {
  historyId: number;
  stationId: string;
  programTitle: string;
  performers: string | null;
  broadcastStartAt: string;
  broadcastEndAt: string;
  durationSeconds: number | null;
  fileSizeBytes: number | null;
  mp3Path: string | null;
  hlsUrl: string | null;
  reDownloadable: boolean;
}

export interface PlaybackPositionResponse {
  positionSeconds: number;
}

export interface BatchExecutionResponse {
  id: number;
  type: BatchType;
  triggeredBy: 'SCHEDULER' | 'WEB' | 'CLI';
  status: BatchStatus;
  summary: string | null;
  startedAt: string;
  finishedAt: string | null;
}

export interface RunBatchRequest {
  type: BatchType;
  options?: { date?: string; force?: boolean };
}

export interface RunBatchResponse {
  batchExecutionId: number;
  status: BatchStatus;
}
