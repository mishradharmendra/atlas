package com.atlas.knowledge.domain.port;

import com.atlas.knowledge.api.SourceQuality;
import java.util.List;

/**
 * Read model for per-source extraction quality.
 *
 * <p>Separate from {@link ReviewQueue} because it is a different shape of question. The queue is
 * worked one item at a time; this aggregates across all of them and across the edges they came
 * from, and serving both from one port would mean either N+1 counting or a queue interface
 * carrying aggregate methods nobody working the queue calls.
 */
public interface SourceQualityRepository {

    SourceQuality forSource(String sourceId);

    /** Every source that has produced at least one edge. The vendor-comparison view. */
    List<SourceQuality> all();
}
