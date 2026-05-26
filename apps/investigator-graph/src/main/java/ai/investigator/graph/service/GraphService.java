package ai.investigator.graph.service;

import ai.investigator.graph.entity.CompanyNode;
import ai.investigator.graph.entity.PersonNode;
import ai.investigator.graph.repository.Neo4jGraphRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * High-level graph operations used by agent tools.
 * Keeps Neo4j driver details out of the agent layer.
 */
@Service
public class GraphService {

    private final Neo4jGraphRepository repo;

    public GraphService(Neo4jGraphRepository repo) {
        this.repo = repo;
    }

    public List<PersonNode> findUBO(String companyName) {
        return repo.findUBO(companyName);
    }

    public List<CompanyNode> findCompaniesByPerson(String fullName) {
        return repo.findCompaniesByPerson(fullName);
    }

    public List<Neo4jGraphRepository.OwnershipEntry> findOwnershipChain(String companyName) {
        return repo.findOwnershipChain(companyName);
    }

    public List<Neo4jGraphRepository.ConflictEntry> detectConflictsOfInterest(
            String from, String to) {
        return repo.detectConflictsOfInterest(from, to);
    }

    public List<Neo4jGraphRepository.ConflictEntry> detectConflictsForPerson(String personName) {
        return repo.detectConflictsForPerson(personName);
    }

    public List<Neo4jGraphRepository.TaxHavenEntry> findTaxHavenConnections(String personName) {
        return repo.findTaxHavenConnections(personName);
    }

    public List<Neo4jGraphRepository.FamilyEntry> findFamilyNetwork(String personName) {
        return repo.findFamilyNetwork(personName);
    }

    public List<Neo4jGraphRepository.AssociationEntry> findAssociatedPersons(String personName) {
        return repo.findAssociatedPersons(personName);
    }

    public List<Neo4jGraphRepository.ConvictionEntry> findConvictions(String personName) {
        return repo.findConvictions(personName);
    }

    public List<Neo4jGraphRepository.ContractEntry> findContractsWonByCompany(String companyName) {
        return repo.findContractsWonByCompany(companyName);
    }

    public List<Neo4jGraphRepository.DocumentRef> findDocumentsForPerson(String personName) {
        return repo.findDocumentsForPerson(personName);
    }

    public Neo4jGraphRepository.PathResult shortestPath(String person1, String person2) {
        return repo.shortestPath(person1, person2);
    }

    public Neo4jGraphRepository.PathResult personToCompanyPath(String person, String company) {
        return repo.personToCompanyPath(person, company);
    }

    public List<String> listAllPersonNames() {
        return repo.listAllPersonNames();
    }

    public List<String> listAllCompanyNames() {
        return repo.listAllCompanyNames();
    }

    public List<Neo4jGraphRepository.ConflictEntry> detectConflictsInRange(String from, String to) {
        return repo.detectConflictsInRange(from, to);
    }

    // --- Ingest helpers ---

    public void ingestPerson(String id, String fullName, LocalDate birthDate, String nationality,
                              String taxCode, String politicalRole, Double riskScore) {
        repo.mergePerson(id, fullName, birthDate, nationality, taxCode, politicalRole, riskScore);
    }

    public void ingestCompany(String id, String name, String registrationNumber,
                               String jurisdiction, String legalForm, boolean active,
                               String vatNumber, String sector) {
        repo.mergeCompany(id, name, registrationNumber, jurisdiction, legalForm, active, vatNumber, sector);
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
