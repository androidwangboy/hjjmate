// 修改人:祝奔；时间：2026-11-26T18:10:47+08:00；修改原因/内容：实现纯前端皮肤配置与IndexedDB自定义壁纸存储适配器及图片压缩工具，预留二期服务端接口；代码来源：AI生成
import { DEFAULT_SKIN_ID, isValidSkinId, type SkinId } from '@/config/skins'

export type WallpaperType = 'none' | 'builtin' | 'custom'

export interface AppearanceConfig {
  skinId: SkinId
  wallpaperType: WallpaperType
  wallpaperId: string
  surfaceOpacity: number
  wallpaperBlur: number
  scrimOpacity: number
}

export interface CustomWallpaperMeta {
  id: string
  name: string
  mimeType: string
  size: number
  width: number
  height: number
  thumbDataUrl: string
  createdAt: number
}

interface StoredWallpaperRecord extends CustomWallpaperMeta {
  blob: Blob
}

export interface AppearanceStorageAdapter {
  loadConfig(): AppearanceConfig | null
  saveConfig(config: AppearanceConfig): void
  listCustomWallpapers(): Promise<CustomWallpaperMeta[]>
  saveCustomWallpaper(file: File): Promise<CustomWallpaperMeta>
  loadCustomWallpaperBlob(id: string): Promise<Blob | null>
  deleteCustomWallpaper(id: string): Promise<void>
}

export const APPEARANCE_STORAGE_KEY = 'mateclaw-appearance'
export const MAX_CUSTOM_WALLPAPERS = 8
export const MAX_RAW_WALLPAPER_BYTES = 15 * 1024 * 1024
export const SUPPORTED_WALLPAPER_TYPES = ['image/jpeg', 'image/png', 'image/webp'] as const

const DB_NAME = 'hjjmate-appearance-db'
const DB_VERSION = 1
const STORE_NAME = 'wallpapers'

export const DEFAULT_APPEARANCE_CONFIG: AppearanceConfig = {
  skinId: DEFAULT_SKIN_ID,
  wallpaperType: 'none',
  wallpaperId: '',
  surfaceOpacity: 0.84,
  wallpaperBlur: 2,
  scrimOpacity: 0.18,
}

export function clampNumber(value: unknown, min: number, max: number, fallback: number): number {
  const num = typeof value === 'number' ? value : Number(value)
  if (!Number.isFinite(num)) return fallback
  return Math.min(max, Math.max(min, num))
}

export function sanitizeAppearanceConfig(raw: Partial<AppearanceConfig> | null | undefined): AppearanceConfig {
  const src = raw || {}
  const wallpaperType: WallpaperType =
    src.wallpaperType === 'builtin' || src.wallpaperType === 'custom' || src.wallpaperType === 'none'
      ? src.wallpaperType
      : DEFAULT_APPEARANCE_CONFIG.wallpaperType
  const wallpaperId = typeof src.wallpaperId === 'string' ? src.wallpaperId : ''
  return {
    skinId: isValidSkinId(src.skinId) ? src.skinId : DEFAULT_APPEARANCE_CONFIG.skinId,
    wallpaperType: wallpaperType !== 'none' && !wallpaperId ? 'none' : wallpaperType,
    wallpaperId: wallpaperType === 'none' ? '' : wallpaperId,
    surfaceOpacity: Number(clampNumber(src.surfaceOpacity, 0.55, 0.98, DEFAULT_APPEARANCE_CONFIG.surfaceOpacity).toFixed(2)),
    wallpaperBlur: Math.round(clampNumber(src.wallpaperBlur, 0, 24, DEFAULT_APPEARANCE_CONFIG.wallpaperBlur)),
    scrimOpacity: Number(clampNumber(src.scrimOpacity, 0, 0.7, DEFAULT_APPEARANCE_CONFIG.scrimOpacity).toFixed(2)),
  }
}

export class WallpaperValidationError extends Error {
  code: 'unsupported_type' | 'file_too_large' | 'decode_failed'
  constructor(code: 'unsupported_type' | 'file_too_large' | 'decode_failed', message: string) {
    super(message)
    this.name = 'WallpaperValidationError'
    this.code = code
  }
}

function loadImageElement(file: File): Promise<{ width: number; height: number; source: CanvasImageSource; cleanup: () => void }> {
  return new Promise((resolve, reject) => {
    const objectUrl = URL.createObjectURL(file)
    const img = new Image()
    img.onload = () => {
      resolve({
        width: img.naturalWidth || img.width,
        height: img.naturalHeight || img.height,
        source: img,
        cleanup: () => URL.revokeObjectURL(objectUrl),
      })
    }
    img.onerror = () => {
      URL.revokeObjectURL(objectUrl)
      reject(new WallpaperValidationError('decode_failed', 'Failed to decode image'))
    }
    img.src = objectUrl
  })
}

async function decodeImageSource(file: File): Promise<{ width: number; height: number; source: CanvasImageSource; cleanup: () => void }> {
  if (typeof createImageBitmap === 'function') {
    try {
      const bitmap = await createImageBitmap(file)
      return {
        width: bitmap.width,
        height: bitmap.height,
        source: bitmap,
        cleanup: () => bitmap.close(),
      }
    } catch {
      return loadImageElement(file)
    }
  }
  return loadImageElement(file)
}

function canvasToBlob(canvas: HTMLCanvasElement, mimeType: string, quality: number): Promise<Blob | null> {
  return new Promise((resolve) => {
    canvas.toBlob((blob) => resolve(blob), mimeType, quality)
  })
}

export async function compressWallpaperFile(file: File): Promise<{
  blob: Blob
  mimeType: string
  width: number
  height: number
  thumbDataUrl: string
}> {
  if (!SUPPORTED_WALLPAPER_TYPES.includes(file.type as (typeof SUPPORTED_WALLPAPER_TYPES)[number])) {
    throw new WallpaperValidationError('unsupported_type', `Unsupported image type: ${file.type || 'unknown'}`)
  }
  if (file.size > MAX_RAW_WALLPAPER_BYTES) {
    throw new WallpaperValidationError('file_too_large', `Image exceeds ${MAX_RAW_WALLPAPER_BYTES} bytes`)
  }

  // Test or non-canvas environment fallback
  if (typeof document === 'undefined') {
    return {
      blob: file,
      mimeType: file.type,
      width: 1920,
      height: 1080,
      thumbDataUrl: '',
    }
  }

  const decoded = await decodeImageSource(file)
  try {
    const maxEdge = 1920
    const scale = Math.min(1, maxEdge / Math.max(decoded.width, decoded.height, 1))
    const targetWidth = Math.max(1, Math.round(decoded.width * scale))
    const targetHeight = Math.max(1, Math.round(decoded.height * scale))

    const canvas = document.createElement('canvas')
    canvas.width = targetWidth
    canvas.height = targetHeight
    const ctx = canvas.getContext('2d')
    if (!ctx) {
      throw new WallpaperValidationError('decode_failed', 'Canvas 2D context unavailable')
    }
    ctx.drawImage(decoded.source, 0, 0, targetWidth, targetHeight)

    let outputMime = 'image/webp'
    let compressed = await canvasToBlob(canvas, 'image/webp', 0.86)
    if (!compressed || compressed.type !== 'image/webp') {
      outputMime = 'image/jpeg'
      compressed = await canvasToBlob(canvas, 'image/jpeg', 0.88)
    }
    if (!compressed) {
      compressed = file
      outputMime = file.type
    }

    // Generate lightweight 320px thumbnail for instant drawer preview
    const thumbScale = Math.min(1, 320 / Math.max(targetWidth, 1))
    const thumbCanvas = document.createElement('canvas')
    thumbCanvas.width = Math.max(1, Math.round(targetWidth * thumbScale))
    thumbCanvas.height = Math.max(1, Math.round(targetHeight * thumbScale))
    const thumbCtx = thumbCanvas.getContext('2d')
    let thumbDataUrl = ''
    if (thumbCtx) {
      thumbCtx.drawImage(canvas, 0, 0, thumbCanvas.width, thumbCanvas.height)
      thumbDataUrl = thumbCanvas.toDataURL('image/jpeg', 0.72)
    }

    return {
      blob: compressed,
      mimeType: outputMime,
      width: targetWidth,
      height: targetHeight,
      thumbDataUrl,
    }
  } finally {
    decoded.cleanup()
  }
}

function generateId(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return `wp-${crypto.randomUUID()}`
  }
  return `wp-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
}

function toMeta(record: StoredWallpaperRecord): CustomWallpaperMeta {
  return {
    id: record.id,
    name: record.name,
    mimeType: record.mimeType,
    size: record.size,
    width: record.width,
    height: record.height,
    thumbDataUrl: record.thumbDataUrl,
    createdAt: record.createdAt,
  }
}

export class LocalAppearanceStorageAdapter implements AppearanceStorageAdapter {
  private memoryFallback = new Map<string, StoredWallpaperRecord>()
  private dbPromise: Promise<IDBDatabase | null> | null = null

  loadConfig(): AppearanceConfig | null {
    try {
      const raw = localStorage.getItem(APPEARANCE_STORAGE_KEY)
      if (!raw) return null
      return sanitizeAppearanceConfig(JSON.parse(raw))
    } catch {
      return null
    }
  }

  saveConfig(config: AppearanceConfig): void {
    try {
      localStorage.setItem(APPEARANCE_STORAGE_KEY, JSON.stringify(sanitizeAppearanceConfig(config)))
    } catch {
      /* ignore quota / privacy errors */
    }
  }

  private openDb(): Promise<IDBDatabase | null> {
    if (typeof indexedDB === 'undefined') {
      return Promise.resolve(null)
    }
    if (this.dbPromise) return this.dbPromise
    this.dbPromise = new Promise((resolve) => {
      try {
        const req = indexedDB.open(DB_NAME, DB_VERSION)
        req.onupgradeneeded = () => {
          const db = req.result
          if (!db.objectStoreNames.contains(STORE_NAME)) {
            db.createObjectStore(STORE_NAME, { keyPath: 'id' })
          }
        }
        req.onsuccess = () => resolve(req.result)
        req.onerror = () => resolve(null)
      } catch {
        resolve(null)
      }
    })
    return this.dbPromise
  }

  async listCustomWallpapers(): Promise<CustomWallpaperMeta[]> {
    const db = await this.openDb()
    if (!db) {
      return Array.from(this.memoryFallback.values())
        .map(toMeta)
        .sort((a, b) => b.createdAt - a.createdAt)
    }
    return new Promise((resolve) => {
      try {
        const tx = db.transaction(STORE_NAME, 'readonly')
        const store = tx.objectStore(STORE_NAME)
        const req = store.getAll()
        req.onsuccess = () => {
          const items = ((req.result || []) as StoredWallpaperRecord[])
            .map(toMeta)
            .sort((a, b) => b.createdAt - a.createdAt)
          resolve(items)
        }
        req.onerror = () => resolve([])
      } catch {
        resolve([])
      }
    })
  }

  async saveCustomWallpaper(file: File): Promise<CustomWallpaperMeta> {
    const compressed = await compressWallpaperFile(file)
    const record: StoredWallpaperRecord = {
      id: generateId(),
      name: file.name || 'custom-wallpaper',
      mimeType: compressed.mimeType,
      size: compressed.blob.size,
      width: compressed.width,
      height: compressed.height,
      thumbDataUrl: compressed.thumbDataUrl,
      createdAt: Date.now(),
      blob: compressed.blob,
    }

    const existing = await this.listCustomWallpapers()
    if (existing.length >= MAX_CUSTOM_WALLPAPERS) {
      const stale = existing.slice(MAX_CUSTOM_WALLPAPERS - 1)
      await Promise.all(stale.map((item) => this.deleteCustomWallpaper(item.id)))
    }

    const db = await this.openDb()
    if (!db) {
      this.memoryFallback.set(record.id, record)
      return toMeta(record)
    }

    return new Promise((resolve, reject) => {
      try {
        const tx = db.transaction(STORE_NAME, 'readwrite')
        const store = tx.objectStore(STORE_NAME)
        const req = store.put(record)
        req.onsuccess = () => resolve(toMeta(record))
        req.onerror = () => {
          this.memoryFallback.set(record.id, record)
          resolve(toMeta(record))
        }
      } catch (e) {
        reject(e)
      }
    })
  }

  async loadCustomWallpaperBlob(id: string): Promise<Blob | null> {
    if (!id) return null
    const mem = this.memoryFallback.get(id)
    if (mem) return mem.blob

    const db = await this.openDb()
    if (!db) return null
    return new Promise((resolve) => {
      try {
        const tx = db.transaction(STORE_NAME, 'readonly')
        const store = tx.objectStore(STORE_NAME)
        const req = store.get(id)
        req.onsuccess = () => {
          const rec = req.result as StoredWallpaperRecord | undefined
          resolve(rec?.blob || null)
        }
        req.onerror = () => resolve(null)
      } catch {
        resolve(null)
      }
    })
  }

  async deleteCustomWallpaper(id: string): Promise<void> {
    this.memoryFallback.delete(id)
    const db = await this.openDb()
    if (!db) return
    await new Promise<void>((resolve) => {
      try {
        const tx = db.transaction(STORE_NAME, 'readwrite')
        const store = tx.objectStore(STORE_NAME)
        const req = store.delete(id)
        req.onsuccess = () => resolve()
        req.onerror = () => resolve()
      } catch {
        resolve()
      }
    })
  }
}

export const defaultAppearanceStorage: AppearanceStorageAdapter = new LocalAppearanceStorageAdapter()
