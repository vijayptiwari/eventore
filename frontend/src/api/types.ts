export type ProtocolType =
  | 'KAFKA'
  | 'MQTT'
  | 'JMS'
  | 'PULSAR'
  | 'RABBITMQ'
  | 'KINESIS'
  | 'GCP_PUBSUB'
  | 'AZURE_SERVICE_BUS';

export type CloudProvider = 'ON_PREM' | 'AWS' | 'AZURE' | 'GCP' | 'OCP';

export type StreamPlatform =
  | 'GENERIC'
  | 'AWS_MSK'
  | 'AWS_KINESIS'
  | 'AWS_MQ'
  | 'AWS_IOT_CORE'
  | 'AWS_SQS'
  | 'AZURE_EVENT_HUBS'
  | 'AZURE_SERVICE_BUS'
  | 'AZURE_IOT_HUB'
  | 'GCP_PUBSUB'
  | 'GCP_MANAGED_KAFKA'
  | 'OCP_AMQ_STREAMS'
  | 'OCP_STRIMZI_KAFKA';
export type DeploymentMode = 'ADMIN' | 'DEV' | 'READONLY';

/** UI cascade from control plane (authoritative for menus and filters). */
export interface ControlPlaneUiCascade {
  connectionProtocols: ProtocolType[];
  inspectProtocols: ProtocolType[];
  adminProtocols: ProtocolType[];
  platformFilterProtocols: ProtocolType[];
}

export interface ControlPlaneView {
  revision: number;
  openApiStreams?: string[];
  uiCascade: ControlPlaneUiCascade;
}

export interface AppConfig {
  deploymentMode: DeploymentMode;
  allowedActions: string[];
  supportedProtocols: ProtocolType[];
  loadedModules?: string[];
  controlPlane?: ControlPlaneView;
}

export interface ConnectionProfile {
  id?: string;
  name: string;
  protocol: ProtocolType;
  cloudProvider?: CloudProvider;
  streamPlatform?: StreamPlatform;
  brokerUrl: string;
  properties?: Record<string, string>;
  credentials?: Record<string, string>;
}

export interface TopicRef {
  name: string;
  type: string;
  protocol: ProtocolType;
}

export interface UnifiedMessage {
  id: string;
  destination: string;
  headers: Record<string, string>;
  payload: string;
  contentType: string;
  timestamp: string;
  protocol: ProtocolType;
  direction: string;
  connectionId: string;
}

export interface StreamFrame {
  type: string;
  subscriptionId?: string;
  clientStreamId?: string;
  message?: UnifiedMessage;
  detail?: string;
}
