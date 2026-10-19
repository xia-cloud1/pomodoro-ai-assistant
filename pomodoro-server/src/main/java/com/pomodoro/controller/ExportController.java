package com.pomodoro.controller;

import com.pomodoro.service.ExportService;
import com.pomodoro.service.PersonalDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static org.springframework.format.annotation.DateTimeFormat.ISO.DATE;

/**
 * 数据导出接口
 */
@RestController
@RequestMapping("/api/export")
@RequiredArgsConstructor
public class ExportController {

    private final ExportService exportService;
    private final PersonalDataService personalDataService;

    /**
     * 导出番茄记录 CSV（默认近 30 天）
     */
    @GetMapping("/pomodoro")
    public ResponseEntity<byte[]> exportPomodoro(
            @RequestParam(required = false) @DateTimeFormat(iso = DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DATE) LocalDate end) {
        byte[] csv = exportService.exportPomodoroCsv(start, end);
        String filename = "pomodoro_" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + ".csv";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv;charset=UTF-8"));
        headers.setContentDispositionFormData("attachment", filename);
        return ResponseEntity.ok().headers(headers).body(csv);
    }

    /**
     * 导出个人全部数据（个人信息权 / 数据可携带，JSON 不含密码）
     */
    @GetMapping("/personal")
    public ResponseEntity<byte[]> exportPersonal() {
        byte[] json = personalDataService.exportPersonalData();
        String filename = "personal_data_" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + ".json";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setContentDispositionFormData("attachment", filename);
        return ResponseEntity.ok().headers(headers).body(json);
    }
}
