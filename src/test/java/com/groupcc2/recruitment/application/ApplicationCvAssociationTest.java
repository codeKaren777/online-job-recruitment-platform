package com.groupcc2.recruitment.application;

import com.groupcc2.recruitment.application.entity.Application;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class ApplicationCvAssociationTest {
    @Test
    void eachApplicationKeepsItsOwnSubmittedCvReference() {
        UUID applicant = UUID.randomUUID();
        UUID jobA = UUID.randomUUID();
        UUID jobB = UUID.randomUUID();
        UUID cvA = UUID.randomUUID();
        UUID cvB = UUID.randomUUID();

        Application first = Application.submit(jobA, applicant, cvA);
        Application second = Application.submit(jobB, applicant, cvB);

        assertThat(first.getCvDocumentId()).isEqualTo(cvA);
        assertThat(second.getCvDocumentId()).isEqualTo(cvB);
        assertThat(first.getJobSeekerId()).isEqualTo(second.getJobSeekerId());
    }

    @Test
    void cvIsRequiredByTheApplicationFactory() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                Application.submit(UUID.randomUUID(), UUID.randomUUID(), null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
