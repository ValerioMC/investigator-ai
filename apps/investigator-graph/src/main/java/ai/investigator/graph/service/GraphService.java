package ai.investigator.graph.service;

import ai.investigator.graph.entity.CompanyNode;
import ai.investigator.graph.entity.PersonNode;
import ai.investigator.graph.projection.AssociationEntry;
import ai.investigator.graph.projection.ConflictEntry;
import ai.investigator.graph.projection.ContractEntry;
import ai.investigator.graph.projection.ConvictionEntry;
import ai.investigator.graph.projection.DocumentRef;
import ai.investigator.graph.projection.FamilyEntry;
import ai.investigator.graph.projection.OwnershipEntry;
import ai.investigator.graph.projection.PathResult;
import ai.investigator.graph.projection.TaxHavenEntry;
import ai.investigator.graph.repository.GraphRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * High-level graph operations used by agent tools.
 * Thin facade over {@link GraphRepository} — same method names the agents already call.
 */
@Service
public class GraphService {

    private final GraphRepository repo;

    public GraphService(GraphRepository repo) {
        this.repo = repo;
    }

    // --- Reads ---

    public List<PersonNode> findUBO(String companyName) {
        return repo.findUBO(companyName);
    }

    public List<CompanyNode> findCompaniesByPerson(String fullName) {
        return repo.findCompaniesByPerson(fullName);
    }

    public List<OwnershipEntry> findOwnershipChain(String companyName) {
        return repo.findOwnershipChain(companyName);
    }

    public List<ConflictEntry> detectConflictsOfInterest(String from, String to) {
        return repo.detectConflictsOfInterest(
            from != null ? from : "1900-01-01",
            to   != null ? to   : "2100-01-01");
    }

    public List<ConflictEntry> detectConflictsForPerson(String personName) {
        return repo.detectConflictsForPerson(personName);
    }

    public List<ConflictEntry> detectConflictsInRange(String from, String to) {
        return repo.detectConflictsInRange(from, to);
    }

    public List<TaxHavenEntry> findTaxHavenConnections(String personName) {
        return repo.findTaxHavenConnections(personName);
    }

    public List<FamilyEntry> findFamilyNetwork(String personName) {
        return repo.findFamilyNetwork(personName);
    }

    public List<AssociationEntry> findAssociatedPersons(String personName) {
        return repo.findAssociatedPersons(personName);
    }

    public List<ConvictionEntry> findConvictions(String personName) {
        return repo.findConvictions(personName);
    }

    public List<ContractEntry> findContractsWonByCompany(String companyName) {
        return repo.findContractsWonByCompany(companyName);
    }

    public List<DocumentRef> findDocumentsForPerson(String personName) {
        return repo.findDocumentsForPerson(personName);
    }

    public PathResult shortestPath(String person1, String person2) {
        return repo.shortestPath(person1, person2).orElse(null);
    }

    public PathResult personToCompanyPath(String person, String company) {
        return repo.personToCompanyPath(person, company).orElse(null);
    }

    public List<String> listAllPersonNames() {
        return repo.listAllPersonNames();
    }

    public List<String> listAllCompanyNames() {
        return repo.listAllCompanyNames();
    }

    // --- Ingest ---

    public void ingestPerson(String id, String fullName, LocalDate birthDate, String nationality,
                              String taxCode, String politicalRole, Double riskScore) {
        repo.mergePerson(id, fullName,
            birthDate != null ? birthDate.toString() : null,
            nationality, taxCode, politicalRole, riskScore);
    }

    public void ingestCompany(String id, String name, String registrationNumber,
                               String jurisdiction, String legalForm, boolean active,
                               String vatNumber, String sector) {
        repo.mergeCompany(id, name, registrationNumber, jurisdiction, legalForm, active,
            vatNumber, sector);
    }

    public void ingestContract(String id, String title, double amount, String awardedAt,
                                String cpvCode, String publicBodyName, String source) {
        repo.mergeContract(id, title, amount, awardedAt, cpvCode, publicBodyName, source);
    }

    public void ingestPublicBody(String id, String name, String level, String country) {
        repo.mergePublicBody(id, name, level, country);
    }

    public void ingestJurisdiction(String id, String name, String isoCode,
                                    boolean taxHaven, boolean euMember) {
        repo.mergeJurisdiction(id, name, isoCode, taxHaven, euMember);
    }

    public void linkOwnership(String personName, String companyName,
                               double sharePercent, String type) {
        repo.mergeOwns(personName, companyName, sharePercent, type);
    }

    public void linkDirectorship(String personName, String companyName,
                                  String role, String from, String to) {
        repo.mergeIsDirectorOf(personName, companyName, role, from, to);
    }

    public void linkPublicRole(String personName, String bodyName,
                                String title, String from, String to) {
        repo.mergeHeldPublicRole(personName, bodyName, title, from, to);
    }

    public void linkContractAwardedTo(String contractTitle, String companyName) {
        repo.mergeContractAwardedTo(contractTitle, companyName);
    }

    public void linkPublicBodyIssuedContract(String bodyName, String contractTitle) {
        repo.mergePublicBodyIssuedContract(bodyName, contractTitle);
    }

    public void linkRegisteredIn(String companyName, String isoCode) {
        repo.mergeRegisteredIn(companyName, isoCode);
    }
}
