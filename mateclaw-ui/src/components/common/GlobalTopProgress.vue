<template>
  <Transition name="progress-fade">
    <div v-if="visible" class="global-top-progress" role="progressbar" aria-label="页面加载中">
      <div class="global-top-progress__bar"></div>
      <div class="global-top-progress__glow"></div>
    </div>
  </Transition>
</template>

<script setup lang="ts">
defineProps<{ visible: boolean }>()
</script>

<style scoped>
.global-top-progress {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  height: 3px;
  z-index: 9999;
  overflow: hidden;
  pointer-events: none;
  background: transparent;
}
.global-top-progress__bar {
  height: 100%;
  width: 38%;
  background: linear-gradient(90deg, var(--mc-primary, #d96d46), var(--mc-accent, #184a45));
  border-radius: 999px;
  animation: global-progress-slide 1.1s ease-in-out infinite;
}
.global-top-progress__glow {
  position: absolute;
  inset: 0;
  background: linear-gradient(90deg, transparent, rgba(217, 109, 70, 0.18), transparent);
  animation: global-progress-glow 1.4s ease-in-out infinite;
}
@keyframes global-progress-slide {
  0% { transform: translateX(-100%); }
  50% { transform: translateX(220%); }
  100% { transform: translateX(-100%); }
}
@keyframes global-progress-glow {
  0% { transform: translateX(-60%); }
  100% { transform: translateX(160%); }
}
.progress-fade-enter-active,
.progress-fade-leave-active {
  transition: opacity 0.2s ease;
}
.progress-fade-enter-from,
.progress-fade-leave-to {
  opacity: 0;
}
@media (prefers-reduced-motion: reduce) {
  .global-top-progress__bar,
  .global-top-progress__glow {
    animation: none;
  }
  .global-top-progress__bar {
    width: 100%;
    opacity: 0.85;
  }
}
</style>
