/** Match inspect capability tokens exactly (avoids substring false positives such as planned ⊃ lag). */
export function hasInspectFeature(features: string[] | undefined, token: string): boolean {
  if (!features?.length) return false;
  return features.includes(token);
}
