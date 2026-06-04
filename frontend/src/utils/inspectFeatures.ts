export function hasInspectFeature(features: string[] | undefined, token: string): boolean {
  if (!features?.length) return false;
  return features.some((f) => f === token || f.includes(token));
}
