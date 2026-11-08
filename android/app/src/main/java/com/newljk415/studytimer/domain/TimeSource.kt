package com.newljk415.studytimer.domain

import java.time.ZoneId

/**
 * 시간 출처 추상화.
 *
 * - [elapsedMillis]: 단조 시계(부팅 이후 경과, 화면 꺼짐/절전 포함). 같은 부팅 안에서는 이 값만으로 경과 시간을 계산한다.
 *   사용자가 시스템 시각을 바꿔도 영향을 받지 않는다.
 * - [wallMillis]: 벽시계(epoch). 날짜 귀속(자정 분리)과, 재부팅으로 단조 시계가 초기화된 경우의 복구에만 사용한다.
 * - [bootId]: 부팅 식별자. 저장된 값과 다르면 단조 기준점이 무효이므로 벽시계로 복구한다.
 */
interface TimeSource {
    fun wallMillis(): Long
    fun elapsedMillis(): Long
    fun bootId(): String
    fun zone(): ZoneId
}
