package com.newljk415.studytimer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.newljk415.studytimer.data.AppSettings
import com.newljk415.studytimer.data.ThemeMode
import com.newljk415.studytimer.domain.Durations

@Composable
fun SettingsScreen(
    settings: AppSettings,
    notificationsAllowed: Boolean,
    onSaveDurations: (Int, Int, (Boolean) -> Unit) -> Unit,
    onTheme: (ThemeMode) -> Unit,
    onKeepScreenOn: (Boolean) -> Unit,
    onSound: (Boolean) -> Unit,
    onVibrate: (Boolean) -> Unit,
    onNotificationSettings: () -> Unit,
    onPrivacyPolicy: () -> Unit,
    onContact: () -> Unit,
    onExit: () -> Unit,
    versionName: String,
    modifier: Modifier = Modifier,
    monetization: @Composable ColumnScope.() -> Unit = {},
) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Section("타이머 시간")
        var focus by rememberSaveable { mutableStateOf(settings.durations.focusMinutes.toString()) }
        var brk by rememberSaveable { mutableStateOf(settings.durations.breakMinutes.toString()) }
        var msg by rememberSaveable { mutableStateOf<String?>(null) }
        LaunchedEffect(settings.durations) {
            focus = settings.durations.focusMinutes.toString(); brk = settings.durations.breakMinutes.toString()
        }
        val f = focus.toIntOrNull(); val b = brk.toIntOrNull()
        val focusErr = f == null || f !in Durations.FOCUS_RANGE
        val breakErr = b == null || b !in Durations.BREAK_RANGE
        OutlinedTextField(
            focus, { focus = it.filter(Char::isDigit).take(3); msg = null },
            label = { Text("집중 (분)") }, singleLine = true, isError = focusErr,
            supportingText = { Text("${Durations.FOCUS_RANGE.first}~${Durations.FOCUS_RANGE.last}분") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            brk, { brk = it.filter(Char::isDigit).take(3); msg = null },
            label = { Text("휴식 (분)") }, singleLine = true, isError = breakErr,
            supportingText = { Text("${Durations.BREAK_RANGE.first}~${Durations.BREAK_RANGE.last}분") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = { onSaveDurations(f!!, b!!) { ok -> msg = if (ok) "저장했습니다." else "유효 범위를 확인하세요." } },
            enabled = !focusErr && !breakErr, modifier = Modifier.heightIn(min = 48.dp),
        ) { Text("시간 저장") }
        Text("변경한 시간은 대기 중인 세션과 이후 세션에 적용됩니다. 진행 중이거나 일시정지된 세션은 시작할 때의 시간을 유지합니다.",
            style = MaterialTheme.typography.bodySmall)
        msg?.let { Text(it, color = MaterialTheme.colorScheme.primary) }

        HorizontalDivider(); Section("화면")
        Column(Modifier.selectableGroup()) {
            listOf(ThemeMode.SYSTEM to "시스템 설정 따름", ThemeMode.LIGHT to "라이트", ThemeMode.DARK to "다크").forEach { (m, label) ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .selectable(settings.theme == m, role = Role.RadioButton) { onTheme(m) },
                    verticalAlignment = Alignment.CenterVertically,
                ) { RadioButton(settings.theme == m, null); Text(label, Modifier.padding(start = 12.dp)) }
            }
        }
        SwitchRow("타이머 실행 중 화면 켜두기", settings.keepScreenOn, onKeepScreenOn)

        HorizontalDivider(); Section("완료 알림")
        SwitchRow("완료 소리", settings.sound, onSound)
        SwitchRow("완료 진동", settings.vibrate, onVibrate)
        Text(
            if (notificationsAllowed) "시스템 알림이 허용되어 있습니다. 앱이 백그라운드일 때 완료를 알립니다(절전 상태에서는 몇 분 늦을 수 있음)."
            else "시스템 알림이 꺼져 있습니다. 타이머와 기록은 그대로 사용할 수 있으며, 앱을 열면 완료 상태가 반영됩니다.",
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedButton(onClick = onNotificationSettings) { Text("알림 설정 열기") }

        monetization()

        HorizontalDivider(); Section("정보")
        OutlinedButton(onClick = onPrivacyPolicy, Modifier.fillMaxWidth()) { Text("개인정보처리방침") }
        OutlinedButton(onClick = onContact, Modifier.fillMaxWidth()) { Text("이메일 문의 (morrowclock@gmail.com)") }
        Text("버전 $versionName", style = MaterialTheme.typography.bodySmall)

        HorizontalDivider()
        Button(onClick = onExit, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("앱 종료") }
    }
}

@Composable
fun Section(title: String) =
    Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f))
        Switch(checked, null)
    }
}
