# 登录页左侧品牌区 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在登录页左侧增加品牌区（主标题 + 副标题 + 六个协同 Tag），桌面端左右分布，窄屏自动隐藏。

**Architecture:** 仅改 `Login.vue`（模板 + scoped 样式 + 一个键名数组）与中英文 i18n 词条；登录逻辑、背景图、右侧卡片零改动。纯视觉变更，无自动化测试，以 dev server 人工验收为准（本仓库没有 Login 视图测试）。

**Tech Stack:** Vue 3 SFC + scoped CSS, vue-i18n, Vite dev server

---

## File Structure

- Modify: `mateclaw-ui/src/i18n/locales/zh-CN.ts` — 在 `login` 下加 `brand` 词条（Task 1）
- Modify: `mateclaw-ui/src/i18n/locales/en-US.ts` — 同上英文版（Task 1）
- Modify: `mateclaw-ui/src/views/Login.vue` — 模板加 `.login-brand` 块 + `brandTags` 数组（Task 2），scoped 样式加品牌区规则 + 响应式隐藏（Task 3）

---

### Task 1: i18n 品牌文案（中英）

**Files:**
- Modify: `mateclaw-ui/src/i18n/locales/zh-CN.ts`（`login` 块内，`failed` 之后加逗号接 `brand`）
- Modify: `mateclaw-ui/src/i18n/locales/en-US.ts`（同位置）

- [ ] **Step 1: 中文词条**

在 `zh-CN.ts` 的 `login` 对象中，`failed: '登录失败，请检查账号密码',` 之后插入：

```ts
    brand: {
      titleLine1: 'AI数智协同',
      titleLine2: '智能体平台',
      subtitle: '为医护减负增效，为家庭加护增康',
      tags: {
        medical: '医护协同',
        nursePatient: '护患协同',
        nurseManage: '护管协同',
        nurseRehab: '护康协同',
        performance: '业考协同',
        quality: '质控协同',
      },
    },
```

- [ ] **Step 2: 英文词条**

在 `en-US.ts` 的 `login` 对象中，`failed: 'Login failed. Please check your credentials.',` 之后插入：

```ts
    brand: {
      titleLine1: 'AI-Powered Synergy',
      titleLine2: 'Agent Platform',
      subtitle: 'Less burden for caregivers, more care for families',
      tags: {
        medical: 'Care-Team Synergy',
        nursePatient: 'Nurse-Patient Synergy',
        nurseManage: 'Nurse-Management Synergy',
        nurseRehab: 'Nurse-Rehab Synergy',
        performance: 'Performance Synergy',
        quality: 'Quality-Control Synergy',
      },
    },
```

- [ ] **Step 3: Commit**

```bash
git add mateclaw-ui/src/i18n/locales/zh-CN.ts mateclaw-ui/src/i18n/locales/en-US.ts
git commit -m "feat(login): 新增左侧品牌区 i18n 文案"
```
Expected: commit 成功，`git status --short` 无残留。

---

### Task 2: Login 模板 + brandTags

**Files:**
- Modify: `mateclaw-ui/src/views/Login.vue`（模板第 1–6 行区域，script setup 的 `bindDialog` 之后）

- [ ] **Step 1: 品牌区模板**

在 `<div class="login-page">` 内、` <div class="login-center">` 之前插入：

```vue
      <div class="login-brand">
        <h1 class="brand-title">{{ t('login.brand.titleLine1') }}<br>{{ t('login.brand.titleLine2') }}</h1>
        <p class="brand-subtitle">{{ t('login.brand.subtitle') }}</p>
        <div class="brand-tags">
          <span v-for="tag in brandTags" :key="tag" class="brand-tag">{{ t(tag) }}</span>
        </div>
      </div>
```

- [ ] **Step 2: brandTags 数组**

在 script setup 中 `bindDialog` 定义之后插入：

```ts
// Left brand panel tags (i18n keys, rendered in order).
const brandTags = [
  'login.brand.tags.medical',
  'login.brand.tags.nursePatient',
  'login.brand.tags.nurseManage',
  'login.brand.tags.nurseRehab',
  'login.brand.tags.performance',
  'login.brand.tags.quality',
] as const
```

- [ ] **Step 3: Commit**

```bash
git add mateclaw-ui/src/views/Login.vue
git commit -m "feat(login): 左侧品牌区模板与标签数组"
```
Expected: commit 成功（样式缺失时页面裸奔是正常的，下一个 Task 补样式）。

---

### Task 3: 品牌区样式 + 响应式

**Files:**
- Modify: `mateclaw-ui/src/views/Login.vue`（scoped style：`.login-page` 的 `justify-content`，`.login-brand*` 新规则，`max-width: 900px` 查询）

- [ ] **Step 1: 页面改为左右分布**

原规则：

```css
  display: flex;
  align-items: center;
  justify-content: flex-end;
```

改为：

```css
  display: flex;
  align-items: center;
  justify-content: space-between;
```

- [ ] **Step 2: 品牌区样式**

在 `.login-center` 规则之前插入：

```css
/* Left brand panel */
.login-brand {
  position: relative;
  z-index: 1;
  max-width: 460px;
  color: #fff;
}

.brand-title {
  font-family: 'Source Han Sans SC', 'MiSans', 'PingFang SC', 'Microsoft YaHei', sans-serif;
  font-size: clamp(28px, 3vw, 32px);
  font-weight: 800;
  line-height: 1.4;
  letter-spacing: 2px;
  text-shadow: 0 0 15px rgba(0, 255, 200, 0.4);
  margin: 0;
}

.brand-subtitle {
  margin: 14px 0 0;
  font-size: 14px;
  letter-spacing: 4px;
  color: rgba(178, 235, 220, 0.7);
}

.brand-tags {
  margin-top: 22px;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.brand-tag {
  text-align: center;
  font-size: 12px;
  padding: 7px 0;
  border-radius: 999px;
  color: rgba(210, 245, 235, 0.9);
  border: 1px solid rgba(0, 255, 200, 0.25);
  background: rgba(0, 255, 200, 0.06);
}
```

- [ ] **Step 3: 窄屏隐藏品牌区**

原 `max-width: 900px` 查询只有：

```css
@media (max-width: 900px) {
  .login-page {
    padding-right: clamp(20px, 4vw, 48px);
  }
}
```

改为：

```css
@media (max-width: 900px) {
  .login-page {
    justify-content: center;
    padding-right: clamp(20px, 4vw, 48px);
  }

  .login-brand {
    display: none;
  }
}
```

- [ ] **Step 4: Commit**

```bash
git add mateclaw-ui/src/views/Login.vue
git commit -m "feat(login): 左侧品牌区样式与窄屏隐藏"
```
Expected: commit 成功。

---

### Task 4: 验收

**Files:** 无改动（验证 + 收尾）。

- [ ] **Step 1: 确认 dev server 存活**

Run: `curl -I --max-time 10 http://localhost:5173/`
Expected: `HTTP/1.1 200 OK`（服务名为 `mateclaw-ui-dev`，若未启动则先启动 `npm run dev`）。

- [ ] **Step 2: 桌面端视觉验收**

打开 `http://localhost:5173/login`（宽屏 > 900px），逐项确认：
1. 左侧出现两行白色加粗大标题，带轻微青色辉光；
2. 副标题淡青色、字距宽；
3. 六个描边胶囊 Tag 成 3 列网格；
4. 右侧登录卡片位置、表单、登录流程与改前一致。

- [ ] **Step 3: 窄屏 + 英文验收**

1. 窗口缩到 ≤900px：品牌区消失，登录卡片居中；
2. 切换英文：标题/副标题/Tag 均为英文且无 `login.brand` 裸 key。

- [ ] **Step 4: 收尾确认**

Run: `git status --short && git log -4 --oneline`
Expected: 工作区干净，4 个新 commit（spec、gitignore、feat×2…实际为 spec + gitignore + Task1–3 共 5 个）按顺序排列。

---

## Self-Review

1. **Spec 覆盖：** 左右分布 / 标题 28~32px 白色加粗无衬线 + 指定 text-shadow / 副标题淡青 70% 14px 宽字距 / 六 Tag 描边胶囊网格 / ≤900px 隐藏 / i18n 不硬编码 / 不加动画 — 分别由 Task 2（结构）、Task 3 Step 1–3（布局样式响应式）、Task 1（i18n）、Task 4（验收）覆盖，无遗漏。
2. **占位符扫描：** 无 TBD/TODO；每步含完整代码与精确命令；无"类似 Task N"式引用（Task 3 Step 3 复述了原规则内容）。
3. **类型一致性：** `brandTags` 为 i18n key 字符串字面量数组，与模板 `t(tag)` 用法一致；i18n 键路径 `login.brand.tags.*` 在 Task 1 与 Task 2 中逐键对应（medical / nursePatient / nurseManage / nurseRehab / performance / quality）。
