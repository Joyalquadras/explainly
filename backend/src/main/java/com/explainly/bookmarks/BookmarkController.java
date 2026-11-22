package com.explainly.bookmarks;

import com.explainly.common.ApiException;
import com.explainly.common.CurrentUser;
import com.explainly.common.PageResponse;
import com.explainly.explanation.VideoChapterRepository;
import com.explainly.video.AnalysisAccess;
import com.explainly.video.VideoAnalysis;
import com.explainly.video.VideoAnalysisRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/bookmarks")
@Tag(name = "Bookmarks")
public class BookmarkController {

    private final BookmarkRepository bookmarks;
    private final AnalysisAccess access;
    private final VideoChapterRepository chapters;
    private final VideoAnalysisRepository analyses;

    public BookmarkController(BookmarkRepository bookmarks, AnalysisAccess access, VideoChapterRepository chapters,
                              VideoAnalysisRepository analyses) {
        this.bookmarks = bookmarks;
        this.access = access;
        this.chapters = chapters;
        this.analyses = analyses;
    }

    public record CreateBookmarkRequest(@NotNull Long analysisId, Long chapterId, @Min(0) Integer timestampSeconds,
                                        @NotBlank @Size(max = 300) String title, @Size(max = 2000) String note) {}

    public record BookmarkDto(Long id, Long analysisId, Long chapterId, Integer timestampSeconds, String title, String note,
                              String videoTitle, String youtubeId, Instant createdAt) {}

    @GetMapping
    @Transactional(readOnly = true)
    public PageResponse<BookmarkDto> list(@RequestParam(required = false) Long analysisId,
                                          @RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "20") int size) {
        Long userId = CurrentUser.id();
        PageRequest pr = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100), Sort.by(Sort.Direction.DESC, "createdAt"));
        var result = analysisId == null ? bookmarks.findByUserId(userId, pr) : bookmarks.findByUserIdAndAnalysisId(userId, analysisId, pr);
        Map<Long, VideoAnalysis> byId = analyses.findAllById(result.getContent().stream().map(Bookmark::getAnalysisId).distinct().toList())
                .stream().collect(Collectors.toMap(VideoAnalysis::getId, Function.identity()));
        return PageResponse.of(result, b -> {
            VideoAnalysis a = byId.get(b.getAnalysisId());
            return new BookmarkDto(b.getId(), b.getAnalysisId(), b.getChapterId(), b.getTimestampSeconds(), b.getTitle(), b.getNote(),
                    a == null ? null : a.getVideo().getTitle(), a == null ? null : a.getVideo().getYoutubeId(), b.getCreatedAt());
        });
    }

    @PostMapping
    @Transactional
    public ResponseEntity<BookmarkDto> create(@Valid @RequestBody CreateBookmarkRequest req) {
        Long userId = CurrentUser.id();
        VideoAnalysis a = access.require(userId, req.analysisId());
        if (req.chapterId() != null) {
            var chapter = chapters.findById(req.chapterId()).orElseThrow(() -> ApiException.notFound("Chapter"));
            if (!chapter.getAnalysis().getId().equals(a.getId())) throw ApiException.badRequest("INVALID_CHAPTER", "That chapter belongs to another video.");
        }
        Bookmark b = new Bookmark();
        b.setUserId(userId);
        b.setAnalysisId(a.getId());
        b.setChapterId(req.chapterId());
        b.setTimestampSeconds(req.timestampSeconds());
        b.setTitle(req.title().trim());
        b.setNote(req.note());
        b = bookmarks.save(b);
        return ResponseEntity.status(HttpStatus.CREATED).body(new BookmarkDto(b.getId(), b.getAnalysisId(), b.getChapterId(),
                b.getTimestampSeconds(), b.getTitle(), b.getNote(), a.getVideo().getTitle(), a.getVideo().getYoutubeId(), b.getCreatedAt()));
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        Bookmark b = bookmarks.findById(id).filter(x -> x.getUserId().equals(CurrentUser.id()))
                .orElseThrow(() -> ApiException.notFound("Bookmark"));
        bookmarks.delete(b);
        return ResponseEntity.noContent().build();
    }
}
