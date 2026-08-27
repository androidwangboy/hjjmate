<template>
  <div class="route-skeleton" aria-hidden="true">
    <!-- Header skeleton -->
    <div class="sk-header">
      <div class="sk-kicker"></div>
      <div class="sk-title"></div>
      <div class="sk-desc"></div>
      <div class="sk-desc short"></div>
    </div>
    <!-- Card skeletons -->
    <div class="sk-card">
      <div class="sk-card-head">
        <div class="sk-avatar"></div>
        <div class="sk-lines">
          <div class="sk-line w70"></div>
          <div class="sk-line w45"></div>
        </div>
        <div class="sk-badge"></div>
      </div>
      <div class="sk-card-body">
        <div class="sk-line w90"></div>
        <div class="sk-line w82"></div>
        <div class="sk-line w64"></div>
      </div>
    </div>
    <div class="sk-grid">
      <div class="sk-stat">
        <div class="sk-line w50"></div>
        <div class="sk-line w30 h16"></div>
        <div class="sk-line w40"></div>
      </div>
      <div class="sk-stat">
        <div class="sk-line w50"></div>
        <div class="sk-line w30 h16"></div>
        <div class="sk-line w40"></div>
      </div>
      <div class="sk-stat">
        <div class="sk-line w50"></div>
        <div class="sk-line w30 h16"></div>
        <div class="sk-line w40"></div>
      </div>
    </div>
    <div class="sk-card small">
      <div class="sk-card-head">
        <div class="sk-avatar sm"></div>
        <div class="sk-lines">
          <div class="sk-line w40"></div>
          <div class="sk-line w30"></div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.route-skeleton {
  padding: 4px 2px;
}
.sk-header {
  margin-bottom: 20px;
}
.sk-kicker {
  width: 88px;
  height: 20px;
  border-radius: 999px;
  background: linear-gradient(90deg, var(--mc-bg-sunken, #ebe3db) 25%, var(--mc-bg, #f6f1ea) 37%, var(--mc-bg-sunken, #ebe3db) 63%);
  background-size: 400% 100%;
  animation: sk-shimmer 1.3s ease-in-out infinite;
  margin-bottom: 14px;
}
.sk-title {
  width: 42%;
  height: 22px;
  border-radius: 8px;
  background: linear-gradient(90deg, var(--mc-bg-sunken, #ebe3db) 25%, var(--mc-bg, #f6f1ea) 37%, var(--mc-bg-sunken, #ebe3db) 63%);
  background-size: 400% 100%;
  animation: sk-shimmer 1.3s ease-in-out infinite;
  margin-bottom: 10px;
}
.sk-desc {
  height: 10px;
  border-radius: 999px;
  background: linear-gradient(90deg, var(--mc-bg-sunken, #ebe3db) 25%, var(--mc-bg, #f6f1ea) 37%, var(--mc-bg-sunken, #ebe3db) 63%);
  background-size: 400% 100%;
  animation: sk-shimmer 1.3s ease-in-out infinite;
  margin-bottom: 8px;
}
.sk-desc.short { width: 58%; }
.sk-card {
  background: var(--mc-bg-elevated, #fff);
  border: 1px solid var(--mc-border-light, #ebe3db);
  border-radius: 16px;
  padding: 16px;
  margin-bottom: 14px;
}
.sk-card.small { padding: 12px 16px; }
.sk-card-head {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}
.sk-avatar {
  width: 40px;
  height: 40px;
  border-radius: 12px;
  background: linear-gradient(90deg, var(--mc-bg-sunken, #ebe3db) 25%, var(--mc-bg, #f6f1ea) 37%, var(--mc-bg-sunken, #ebe3db) 63%);
  background-size: 400% 100%;
  animation: sk-shimmer 1.3s ease-in-out infinite;
  flex-shrink: 0;
}
.sk-avatar.sm { width: 28px; height: 28px; border-radius: 8px; }
.sk-lines {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.sk-line {
  height: 10px;
  border-radius: 999px;
  background: linear-gradient(90deg, var(--mc-bg-sunken, #ebe3db) 25%, var(--mc-bg, #f6f1ea) 37%, var(--mc-bg-sunken, #ebe3db) 63%);
  background-size: 400% 100%;
  animation: sk-shimmer 1.3s ease-in-out infinite;
}
.sk-line.h16 { height: 16px; }
.sk-line.w30 { width: 30%; }
.sk-line.w40 { width: 40%; }
.sk-line.w45 { width: 45%; }
.sk-line.w50 { width: 50%; }
.sk-line.w64 { width: 64%; }
.sk-line.w70 { width: 70%; }
.sk-line.w82 { width: 82%; }
.sk-line.w90 { width: 90%; }
.sk-badge {
  width: 56px;
  height: 22px;
  border-radius: 999px;
  background: linear-gradient(90deg, var(--mc-bg-sunken, #ebe3db) 25%, var(--mc-bg, #f6f1ea) 37%, var(--mc-bg-sunken, #ebe3db) 63%);
  background-size: 400% 100%;
  animation: sk-shimmer 1.3s ease-in-out infinite;
}
.sk-card-body {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding-left: 52px;
}
.sk-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  margin-bottom: 14px;
}
.sk-stat {
  background: var(--mc-bg-elevated, #fff);
  border: 1px solid var(--mc-border-light, #ebe3db);
  border-radius: 14px;
  padding: 14px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
@media (max-width: 768px) {
  .sk-grid { grid-template-columns: 1fr; }
  .sk-title { width: 68%; }
}
@keyframes sk-shimmer {
  0% { background-position: 100% 0; }
  100% { background-position: 0 0; }
}
@media (prefers-reduced-motion: reduce) {
  .sk-kicker,
  .sk-title,
  .sk-desc,
  .sk-avatar,
  .sk-line,
  .sk-badge {
    animation: none;
    background: var(--mc-bg-sunken, #ebe3db);
  }
}
</style>
