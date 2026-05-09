<script setup>
import { RouterView } from 'vue-router'
import ToastContainer from './components/ToastContainer.vue'
import SplashOverlay from './components/SplashOverlay.vue'
</script>

<template>
  <!-- D1-W26-04 移动端首屏闪屏（桌面端 / 已显示过即不再渲染） -->
  <SplashOverlay />
  <RouterView />
  <ToastContainer />
</template>
