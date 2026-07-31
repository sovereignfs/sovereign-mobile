/**
 * Instance persistence via @capacitor/preferences — native key-value storage
 * (UserDefaults on iOS, SharedPreferences on Android), surviving app restarts
 * and updates. Mirrors sovereign-desktop's src/store.ts (same shape, same
 * function names) but swaps @tauri-apps/plugin-store's JSON-file store for
 * Preferences' string key-value API — this module is the only place that
 * difference is visible.
 */
import { Preferences } from '@capacitor/preferences';

export interface InstanceEntry {
  /** Instance origin, e.g. `https://my.sovereign.example`. */
  url: string;
  /** Display label — the instance host. */
  label: string;
  /** Unix epoch ms when the instance was added. */
  addedAt: number;
}

const KEY_INSTANCES = 'sovereign.instances';
const KEY_ACTIVE_URL = 'sovereign.activeUrl';

export async function listInstances(): Promise<InstanceEntry[]> {
  const { value } = await Preferences.get({ key: KEY_INSTANCES });
  if (value === null) return [];
  try {
    const parsed: unknown = JSON.parse(value);
    return Array.isArray(parsed) ? (parsed as InstanceEntry[]) : [];
  } catch {
    return [];
  }
}

async function saveInstances(instances: InstanceEntry[]): Promise<void> {
  await Preferences.set({ key: KEY_INSTANCES, value: JSON.stringify(instances) });
}

export async function getActiveUrl(): Promise<string | null> {
  const { value } = await Preferences.get({ key: KEY_ACTIVE_URL });
  return value;
}

export async function setActiveUrl(url: string | null): Promise<void> {
  if (url === null) {
    await Preferences.remove({ key: KEY_ACTIVE_URL });
  } else {
    await Preferences.set({ key: KEY_ACTIVE_URL, value: url });
  }
}

/** Add an instance (no-op when the URL is already stored) and make it active. */
export async function addInstance(entry: InstanceEntry): Promise<void> {
  const instances = await listInstances();
  if (!instances.some((i) => i.url === entry.url)) {
    instances.push(entry);
    await saveInstances(instances);
  }
  await setActiveUrl(entry.url);
}

/** Remove an instance; clears the active URL when it pointed at the removed entry. */
export async function removeInstance(url: string): Promise<void> {
  const instances = (await listInstances()).filter((i) => i.url !== url);
  await saveInstances(instances);
  if ((await getActiveUrl()) === url) {
    await setActiveUrl(null);
  }
}
