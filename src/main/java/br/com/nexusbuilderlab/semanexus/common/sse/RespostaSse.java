package br.com.nexusbuilderlab.semanexus.common.sse;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Resposta HTTP padrão dos streams SSE da API (agentes — Issue #135; notificações — Issue #148):
 * {@code text/event-stream}, {@code Cache-Control: no-cache, no-transform} e {@code X-Accel-Buffering: no} para o
 * Nginx do Web não acumular eventos.
 */
public final class RespostaSse {

    private RespostaSse() {
    }

    public static ResponseEntity<SseEmitter> de(SseEmitter emissor) {
        return ResponseEntity.ok()
                .header("X-Accel-Buffering", "no")
                .cacheControl(CacheControl.noCache().noTransform())
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(emissor);
    }
}
