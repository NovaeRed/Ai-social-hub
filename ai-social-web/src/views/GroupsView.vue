<script setup lang="ts">
import GroupWorkspace from '../components/GroupWorkspace.vue';
import { toRefs } from 'vue';
import { useWorkspaceStore } from '../stores/workspace';
const { user, friends, enterGroup, notify, load } = toRefs(useWorkspaceStore());
</script>
<template>
  <GroupWorkspace
    v-if="user.public_id"
    :user="user"
    :friends="friends"
    @chat="enterGroup"
    @changed="load"
    @notify="notify"
  />
</template>
