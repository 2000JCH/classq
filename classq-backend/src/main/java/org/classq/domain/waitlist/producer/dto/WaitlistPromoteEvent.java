package org.classq.domain.waitlist.producer.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class WaitlistPromoteEvent {

    private Long courseId;
}
