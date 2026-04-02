package com.chuka.irir.service;

import com.chuka.irir.dto.ProjectCreateDto;
import com.chuka.irir.model.Project;
import com.chuka.irir.model.ProjectStatus;
import com.chuka.irir.model.Role;
import com.chuka.irir.model.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.time.Year;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

    @Mock
    private com.chuka.irir.repository.ProjectRepository projectRepository;

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private LuceneIndexService luceneIndexService;

    @Mock
    private SimilarityDetectionService similarityDetectionService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private KeywordExtractionService keywordExtractionService;

    @InjectMocks
    private ProjectService projectService;

    @Test
    void createProject_combinesUploadedFileTextAndIndexesDraftForRecommendations() {
        User student = User.builder()
                .id(10L)
                .email("student@chuka.ac.ke")
                .password("encoded-password")
                .firstName("Test")
                .lastName("Student")
                .studentId("CS/001/2026")
                .role(Role.STUDENT)
                .build();

        MockMultipartFile proposal = new MockMultipartFile(
                "files",
                "proposal.pdf",
                "application/pdf",
                "proposal".getBytes()
        );
        MockMultipartFile appendix = new MockMultipartFile(
                "files",
                "appendix.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "appendix".getBytes()
        );

        ProjectCreateDto dto = new ProjectCreateDto();
        dto.setTitle("AI Research Assistant");
        dto.setAbstractText("A project about academic writing support.");
        dto.setKeywords("AI, NLP, Education");
        dto.setFiles(new MockMultipartFile[]{proposal, appendix});

        when(fileStorageService.store(proposal)).thenReturn(new FileStorageService.StoredFile(
                "proposal.pdf",
                "application/pdf",
                120L,
                "/tmp/uploads/proposal.pdf",
                "natural language processing for academic writing"
        ));
        when(fileStorageService.store(appendix)).thenReturn(new FileStorageService.StoredFile(
                "appendix.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                80L,
                "/tmp/uploads/appendix.docx",
                "student feedback and citation analysis"
        ));
        when(projectRepository.save(any(Project.class))).thenAnswer(invocation -> {
            Project project = invocation.getArgument(0);
            project.setId(101L);
            return project;
        });

        Project saved = projectService.createProject(dto, student);

        assertThat(saved.getId()).isEqualTo(101L);
        assertThat(saved.getStatus()).isEqualTo(ProjectStatus.DRAFT);
        assertThat(saved.getSubmittedBy()).isEqualTo(student);
        assertThat(saved.getAcademicYear()).isEqualTo(Year.now().getValue());
        assertThat(saved.getKeywords()).containsExactlyInAnyOrder("ai", "nlp", "education");
        assertThat(saved.getFiles()).hasSize(2);
        assertThat(saved.getFiles())
                .extracting(file -> file.getFileName())
                .containsExactly("proposal.pdf", "appendix.docx");
        assertThat(saved.getExtractedText())
                .isEqualTo("natural language processing for academic writing\n\n---\n\nstudent feedback and citation analysis");

        ArgumentCaptor<Project> savedProjectCaptor = ArgumentCaptor.forClass(Project.class);
        verify(projectRepository).save(savedProjectCaptor.capture());
        assertThat(savedProjectCaptor.getValue().getFiles()).hasSize(2);

        verify(luceneIndexService).indexDocument(
                eq(saved),
                eq("natural language processing for academic writing\n\n---\n\nstudent feedback and citation analysis")
        );
    }
}
