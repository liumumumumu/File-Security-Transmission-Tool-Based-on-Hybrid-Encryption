<template>
  <svg
    class="recipient-toggle-icon"
    :class="{ open }"
    viewBox="0 0 24 24"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    aria-hidden="true"
  >
    <path d="M4 8L12 16L20 8" />
  </svg>
</template>

<script>
export default {
  name: "RecipientToggleIcon",
  props: {
    open: {
      type: Boolean,
      default: false,
    },
  },
};
</script>
