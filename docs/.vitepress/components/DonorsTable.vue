<script setup lang="ts">
import { donors } from '../data/donors'

withDefaults(
  defineProps<{
    nameLabel?: string
    amountLabel?: string
    dateLabel?: string
    messageLabel?: string
  }>(),
  {
    nameLabel: '昵称',
    amountLabel: '金额',
    dateLabel: '日期',
    messageLabel: '留言'
  }
)
</script>

<template>
  <table>
    <thead>
      <tr>
        <th>{{ nameLabel }}</th>
        <th style="text-align: right">{{ amountLabel }}</th>
        <th>{{ dateLabel }}</th>
        <th>{{ messageLabel }}</th>
      </tr>
    </thead>
    <tbody>
      <tr v-for="donor in donors" :key="`${donor.name}-${donor.date}-${donor.amount}`">
        <td>{{ donor.name }}</td>
        <td style="text-align: right">{{ donor.amount }}</td>
        <td>{{ donor.date }}</td>
        <td>{{ donor.message }}</td>
      </tr>
    </tbody>
  </table>
</template>
