package com.chuka.irir.service;

import com.chuka.irir.dto.CollaboratorDTO;
import com.chuka.irir.model.Project;
import com.chuka.irir.model.ProjectStatus;
import com.chuka.irir.model.Role;
import com.chuka.irir.model.User;
import com.chuka.irir.repository.ProjectRepository;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.document.Field;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CollaboratorRecommendationServiceTest {

    @Mock
    private LuceneIndexService luceneIndexService;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private UserService userService;

    @Test
    void getRecommendations_returnsMatchingCollaboratorAfterProjectUploadEvenWhenPeerProjectIsDraft() throws IOException {
        StandardAnalyzer analyzer = new StandardAnalyzer();
        Directory directory = new ByteBuffersDirectory();

        try (IndexWriter writer = new IndexWriter(directory, new IndexWriterConfig(analyzer))) {
            indexProject(writer, 200L, "NLP Citation Assistant", "nlp citations research",
                    "natural language processing citation mining academic writing research");
            indexProject(writer, 201L, "Crop Yield Tracker", "agriculture sensors",
                    "farm irrigation soil sensors crop monitoring");
            writer.commit();
        }

        User currentStudent = User.builder()
                .id(1L)
                .email("alice@chuka.ac.ke")
                .password("encoded-password")
                .firstName("Alice")
                .lastName("Researcher")
                .studentId("CS/101/2026")
                .researchInterests("natural language processing academic writing")
                .role(Role.STUDENT)
                .build();

        User recommendedStudent = User.builder()
                .id(2L)
                .email("bob@chuka.ac.ke")
                .password("encoded-password")
                .firstName("Bob")
                .lastName("Collaborator")
                .studentId("CS/102/2026")
                .department("Computer Science")
                .researchInterests("citation analysis and information retrieval")
                .role(Role.STUDENT)
                .build();

        Project uploadedProject = Project.builder()
                .id(100L)
                .title("Academic Writing Assistant")
                .abstractText("Helps students with citations and literature support.")
                .keywords(Set.of("nlp", "citations", "research"))
                .submittedBy(currentStudent)
                .status(ProjectStatus.DRAFT)
                .build();

        Project candidateProject = Project.builder()
                .id(200L)
                .title("NLP Citation Assistant")
                .submittedBy(recommendedStudent)
                .status(ProjectStatus.DRAFT)
                .build();

        when(userService.findById(1L)).thenReturn(currentStudent);
        when(projectRepository.findBySubmittedByOrderByCreatedAtDesc(currentStudent))
                .thenReturn(List.of(uploadedProject));
        when(projectRepository.findById(200L)).thenReturn(Optional.of(candidateProject));
        when(luceneIndexService.getDirectory()).thenReturn(directory);
        when(luceneIndexService.getAnalyzer()).thenReturn(analyzer);

        CollaboratorRecommendationService service = new CollaboratorRecommendationService(
                luceneIndexService,
                projectRepository,
                userService
        );

        List<CollaboratorDTO> recommendations = service.getRecommendations(1L);

        assertThat(recommendations).hasSize(1);

        CollaboratorDTO recommendation = recommendations.get(0);
        assertThat(recommendation.getUserId()).isEqualTo(2L);
        assertThat(recommendation.getFullName()).isEqualTo("Bob Collaborator");
        assertThat(recommendation.getMatchingProjects()).containsExactly("NLP Citation Assistant");
        assertThat(recommendation.getOverlapScore()).isGreaterThan(0.0);
        assertThat(recommendation.getContactEmail()).isEqualTo("bob@chuka.ac.ke");
    }

    private void indexProject(IndexWriter writer, Long projectId, String title, String keywords, String fullContent)
            throws IOException {
        writer.addDocument(List.of(
                new StringField(LuceneIndexService.FIELD_PROJECT_ID, String.valueOf(projectId), Field.Store.YES),
                new TextField(LuceneIndexService.FIELD_TITLE, title, Field.Store.YES),
                new TextField(LuceneIndexService.FIELD_KEYWORDS, keywords, Field.Store.YES),
                new TextField(LuceneIndexService.FIELD_FULL_CONTENT, fullContent, Field.Store.NO)
        ));
    }
}
