// 修改人:祝奔；时间：2026-11-26T18:10:47+08:00；修改原因/内容：新增多皮肤预设与内置背景壁纸注册表；代码来源：AI生成
import { withCtx } from '@/utils/appPaths'

export type SkinId = 'medical-ice' | 'warm-sand' | 'deep-ocean' | 'mint-clinic' | 'slate-ink'

export const DEFAULT_SKIN_ID: SkinId = 'medical-ice'

export interface SkinPreviewPalette {
  bg: string
  sidebar: string
  surface: string
  primary: string
  accent: string
  text: string
}

export interface SkinPreset {
  id: SkinId
  nameKey: string
  descKey: string
  recommendedMode: 'light' | 'dark'
  preview: SkinPreviewPalette
  defaultWallpaperId: string
}

export interface BuiltinWallpaper {
  id: string
  nameKey: string
  path: string
  thumbnailBg: string
  tone: 'light' | 'dark'
}

export const SKIN_PRESETS: SkinPreset[] = [
  {
    id: 'medical-ice',
    nameKey: 'appearance.skins.medicalIce.name',
    descKey: 'appearance.skins.medicalIce.desc',
    recommendedMode: 'light',
    preview: {
      bg: '#E8F3FC',
      sidebar: '#F5FAFF',
      surface: '#FFFFFF',
      primary: '#168EDB',
      accent: '#079EAA',
      text: '#173B5C',
    },
    defaultWallpaperId: 'ice-aurora',
  },
  {
    id: 'warm-sand',
    nameKey: 'appearance.skins.warmSand.name',
    descKey: 'appearance.skins.warmSand.desc',
    recommendedMode: 'light',
    preview: {
      bg: '#F6F1EA',
      sidebar: '#FFFDF9',
      surface: '#FFFFFF',
      primary: '#D96D46',
      accent: '#184A45',
      text: '#1D1612',
    },
    defaultWallpaperId: 'warm-silk',
  },
  {
    id: 'deep-ocean',
    nameKey: 'appearance.skins.deepOcean.name',
    descKey: 'appearance.skins.deepOcean.desc',
    recommendedMode: 'dark',
    preview: {
      bg: '#091525',
      sidebar: '#0E2038',
      surface: '#132A47',
      primary: '#38BDF8',
      accent: '#06B6D4',
      text: '#E6F1FA',
    },
    defaultWallpaperId: 'deep-horizon',
  },
  {
    id: 'mint-clinic',
    nameKey: 'appearance.skins.mintClinic.name',
    descKey: 'appearance.skins.mintClinic.desc',
    recommendedMode: 'light',
    preview: {
      bg: '#EAF6F4',
      sidebar: '#F5FCFA',
      surface: '#FFFFFF',
      primary: '#0D9488',
      accent: '#0284C7',
      text: '#133A37',
    },
    defaultWallpaperId: 'mint-breeze',
  },
  {
    id: 'slate-ink',
    nameKey: 'appearance.skins.slateInk.name',
    descKey: 'appearance.skins.slateInk.desc',
    recommendedMode: 'dark',
    preview: {
      bg: '#10141C',
      sidebar: '#171C26',
      surface: '#1D2430',
      primary: '#3B82F6',
      accent: '#14B8A6',
      text: '#EEF2F8',
    },
    defaultWallpaperId: 'deep-horizon',
  },
]

export const BUILTIN_WALLPAPERS: BuiltinWallpaper[] = [
  {
    id: 'medical-desk',
    nameKey: 'appearance.wallpapers.medicalDesk',
    path: '/images/login/hujiamate-login-bg-pc-new.png',
    thumbnailBg: 'linear-gradient(135deg, #cde6fb 0%, #eff8ff 60%, #9ad9f8 100%)',
    tone: 'light',
  },
  {
    id: 'ice-aurora',
    nameKey: 'appearance.wallpapers.iceAurora',
    path: '/images/wallpapers/ice-aurora.svg',
    thumbnailBg: 'linear-gradient(135deg, #e0f2fe 0%, #bae6fd 55%, #ccfbf1 100%)',
    tone: 'light',
  },
  {
    id: 'mint-breeze',
    nameKey: 'appearance.wallpapers.mintBreeze',
    path: '/images/wallpapers/mint-breeze.svg',
    thumbnailBg: 'linear-gradient(135deg, #ecfdf5 0%, #ccfbf1 55%, #e0f2fe 100%)',
    tone: 'light',
  },
  {
    id: 'deep-horizon',
    nameKey: 'appearance.wallpapers.deepHorizon',
    path: '/images/wallpapers/deep-horizon.svg',
    thumbnailBg: 'linear-gradient(135deg, #091525 0%, #0f2744 60%, #0c4a6e 100%)',
    tone: 'dark',
  },
  {
    id: 'warm-silk',
    nameKey: 'appearance.wallpapers.warmSilk',
    path: '/images/wallpapers/warm-silk.svg',
    thumbnailBg: 'linear-gradient(135deg, #f7f1e8 0%, #f1decb 60%, #dbeae6 100%)',
    tone: 'light',
  },
]

export function isValidSkinId(value: unknown): value is SkinId {
  return typeof value === 'string' && SKIN_PRESETS.some((s) => s.id === value)
}

export function getSkinPreset(id: SkinId): SkinPreset {
  return SKIN_PRESETS.find((s) => s.id === id) || SKIN_PRESETS[0]
}

export function getBuiltinWallpaper(id: string): BuiltinWallpaper | undefined {
  return BUILTIN_WALLPAPERS.find((w) => w.id === id)
}

export function resolveBuiltinWallpaperUrl(id: string): string {
  const preset = getBuiltinWallpaper(id)
  return preset ? withCtx(preset.path) : ''
}
