package tn.esprit.examen.nomPrenomClasseExamen.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tn.esprit.examen.nomPrenomClasseExamen.entities.ManualExpense;
import tn.esprit.examen.nomPrenomClasseExamen.entities.Sites;
import tn.esprit.examen.nomPrenomClasseExamen.exception.ResourceNotFoundException;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.ManualExpenseRepository;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.SitesRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * FIX 2: an invalid Site id on create/update must raise {@link ResourceNotFoundException}
 * (-> 404 via GlobalExceptionHandler), not a raw {@code RuntimeException} (-> 500).
 */
@ExtendWith(MockitoExtension.class)
class ManualExpenseServiceTest {

    @Mock private ManualExpenseRepository manualExpenseRepository;
    @Mock private SitesRepository sitesRepository;
    @InjectMocks private ManualExpenseService service;

    @Test
    void create_missingSite_throwsResourceNotFound() {
        when(sitesRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(new ManualExpense(), 99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Site")
                .hasMessageContaining("99");
    }

    @Test
    void create_existingSite_bindsSiteAndSaves() {
        Sites site = new Sites();
        site.setIdSite(3L);
        when(sitesRepository.findById(3L)).thenReturn(Optional.of(site));
        when(manualExpenseRepository.save(any(ManualExpense.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ManualExpense result = service.create(new ManualExpense(), 3L);

        assertThat(result.getSite()).isSameAs(site);
    }

    @Test
    void update_missingSite_throwsResourceNotFound() {
        ManualExpense existing = new ManualExpense();
        existing.setId(1L);
        when(manualExpenseRepository.findById(1L)).thenReturn(Optional.of(existing));

        Sites requestedSite = new Sites();
        requestedSite.setIdSite(42L);
        ManualExpense update = new ManualExpense();
        update.setSite(requestedSite);
        when(sitesRepository.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(1L, update))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Site")
                .hasMessageContaining("42");
    }
}
