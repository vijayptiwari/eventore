import { afterEach, describe, expect, it } from 'vitest';
import {
  API_TOKEN_STORAGE_KEY,
  clearApiToken,
  getRuntimeConfig,
  getStoredApiToken,
  saveApiToken,
} from './runtime';

describe('runtime api token storage', () => {
  const originalConfig = window.__EVENTORE_CONFIG__;

  afterEach(() => {
    window.__EVENTORE_CONFIG__ = originalConfig;
    sessionStorage.clear();
  });

  it('saveApiToken and clearApiToken round-trip via sessionStorage', () => {
    window.__EVENTORE_CONFIG__ = {};
    saveApiToken('session-token');
    expect(getStoredApiToken()).toBe('session-token');
    expect(getRuntimeConfig().apiToken).toBe('session-token');
    clearApiToken();
    expect(getStoredApiToken()).toBeUndefined();
    expect(getRuntimeConfig().apiToken).toBeUndefined();
  });

  it('injected apiToken takes precedence over sessionStorage', () => {
    window.__EVENTORE_CONFIG__ = { apiToken: 'injected-token' };
    sessionStorage.setItem(API_TOKEN_STORAGE_KEY, 'stored-token');
    expect(getRuntimeConfig().apiToken).toBe('injected-token');
  });
});
