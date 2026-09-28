package com.atlas.skill.adapter.in.web;

import com.atlas.skill.api.PublishSkillCommand;
import com.atlas.skill.api.SkillApi;
import com.atlas.skill.api.SkillView;
import java.util.Map;
import java.util.NoSuchElementException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Driving adapter: how a skill gets authored, frozen and read back. */
@RestController
@RequestMapping("/api/v1/skills")
class SkillController {

    private final SkillApi skills;

    SkillController(SkillApi skills) {
        this.skills = skills;
    }

    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    Map<String, String> publish(@RequestBody PublishSkillCommand command) {
        return Map.of("versionedId", skills.publish(command));
    }

    @GetMapping(value = "/{skillId}/versions/{version}", produces = MediaType.APPLICATION_JSON_VALUE)
    SkillView find(@PathVariable String skillId, @PathVariable int version) {
        return skills.find(skillId, version);
    }

    @ExceptionHandler(NoSuchElementException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    String notFound(NoSuchElementException e) {
        return e.getMessage();
    }

    /**
     * Republishing a version with different steps is a 409, not a 400.
     *
     * <p>The payload is well-formed; the conflict is with something already published. A 400
     * sends someone to check their JSON, when what they need to do is pick a new version number.
     */
    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    String conflict(IllegalStateException e) {
        return e.getMessage();
    }

    /** A skill whose steps contradict each other is unrepresentable, not merely malformed. */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    String inadmissible(IllegalArgumentException e) {
        return e.getMessage();
    }
}
