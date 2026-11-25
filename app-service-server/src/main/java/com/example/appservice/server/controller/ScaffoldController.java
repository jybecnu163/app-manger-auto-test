package com.example.appservice.server.controller;

import com.example.appservice.server.service.ScaffoldService;
import com.example.appservice.server.vo.ScaffoldRequest;
import com.example.appservice.server.vo.ScaffoldResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/admin/scaffolds")
@RequiredArgsConstructor
public class ScaffoldController {

    private final ScaffoldService scaffoldService;

    @PostMapping
    public ResponseEntity<byte[]> generate(@RequestBody ScaffoldRequest request) {
        if (!StringUtils.hasText(request.getServerUrl())) {
            request.setServerUrl(ServletUriComponentsBuilder.fromCurrentContextPath()
                    .build().toUriString());
        }
        ScaffoldResult result = scaffoldService.generate(request);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/zip"));
        headers.setContentDisposition(
                ContentDisposition.attachment().filename(result.getFileName()).build());
        headers.add("X-Generated-App-Id", result.getAppId());
        headers.add("X-Generated-Token", result.getToken());
        return new ResponseEntity<>(result.getContent(), headers, HttpStatus.OK);
    }
}
