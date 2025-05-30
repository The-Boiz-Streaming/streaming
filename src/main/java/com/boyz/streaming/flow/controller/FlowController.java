package com.boyz.streaming.flow.controller;

import com.boyz.streaming.flow.service.S3Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RequiredArgsConstructor
@Slf4j
@RestController
@RequestMapping("/flow")
public class FlowController {
    final S3Service s3Service;

    @GetMapping("/status")
    public Mono<?> hello() {
        log.info("hello");
        return Mono.just("OK");
    }

    @GetMapping(value = "/{filename}", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public Mono<ResponseEntity<Flux<DataBuffer>>> getSong(@PathVariable String filename,  @RequestHeader(value = "Range", required = false) String rangeHeader) {
        return s3Service.streamSong(filename + ".mp3", rangeHeader);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequestMapping("/upload")
    public Mono<ResponseEntity<String>> upload(@RequestPart("file") FilePart filePart) {
        UUID uuid = UUID.randomUUID();
        log.debug("Endpoint: {}", s3Service.getEndpoint());

        return Mono.just(filePart)
            .filter(fp -> fp.filename().endsWith(".mp3"))
            .switchIfEmpty(Mono.error(new IllegalArgumentException("Only MP3 files are allowed")))
            .then(DataBufferUtils.join(filePart.content()))
            .map(dataBuffer -> {
                byte[] bytes = new byte[dataBuffer.readableByteCount()];
                dataBuffer.read(bytes);
                DataBufferUtils.release(dataBuffer);
                return bytes;
            })
            .flatMap(bytes -> s3Service.uploadSong(bytes, uuid.toString() + ".mp3"))
            .map(response -> ResponseEntity.ok("File uploaded successfully with id: " + uuid))
            .onErrorResume(e -> {
                if (e instanceof IllegalArgumentException) {
                    return Mono.just(ResponseEntity.badRequest().body(e.getMessage()));
                }
                log.error("Error uploading file", e);
                return Mono.just(ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error uploading file: " + e.getMessage()));
            });
    }
}
