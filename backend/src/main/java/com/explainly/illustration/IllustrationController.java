package com.explainly.illustration;

import com.explainly.common.ApiException;
import com.explainly.common.CurrentUser;
import com.explainly.video.AnalysisAccess;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Base64;

@RestController
@RequestMapping("/api/illustrations")
@Tag(name = "Illustrations")
public class IllustrationController {

    private final IllustrationRepository illustrations;
    private final AnalysisAccess access;

    public IllustrationController(IllustrationRepository illustrations, AnalysisAccess access) {
        this.illustrations = illustrations;
        this.access = access;
    }

    @GetMapping(value = "/{id}/svg", produces = "image/svg+xml")
    @Transactional(readOnly = true)
    public ResponseEntity<String> svg(@PathVariable Long id) {
        Illustration ill = load(id);
        if (ill.getSvg() == null) throw ApiException.notFound("Diagram");
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePrivate())
                .header("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'")
                .contentType(MediaType.valueOf("image/svg+xml")).body(ill.getSvg());
    }

    @GetMapping("/{id}/image")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> image(@PathVariable Long id) {
        Illustration ill = load(id);
        if (ill.getImageData() == null) throw ApiException.notFound("Illustration image");
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePrivate())
                .contentType(MediaType.parseMediaType(ill.getImageMime() == null ? "image/png" : ill.getImageMime()))
                .body(Base64.getDecoder().decode(ill.getImageData()));
    }

    private Illustration load(Long id) {
        Illustration ill = illustrations.findById(id).orElseThrow(() -> ApiException.notFound("Illustration"));
        access.require(CurrentUser.id(), ill.getAnalysis().getId());
        return ill;
    }
}
