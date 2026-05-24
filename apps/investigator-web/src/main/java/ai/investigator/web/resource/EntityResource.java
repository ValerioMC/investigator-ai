package ai.investigator.web.resource;

import ai.investigator.graph.service.GraphService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/web/v1/entities")
public class EntityResource {

    private final GraphService graph;

    public EntityResource(GraphService graph) {
        this.graph = graph;
    }

    @GetMapping("/persons/{name}/companies")
    public ResponseEntity<?> companiesByPerson(@PathVariable String name) {
        return ResponseEntity.ok(graph.findCompaniesByPerson(name));
    }

    @GetMapping("/persons/{name}/conflicts")
    public ResponseEntity<?> conflictsForPerson(@PathVariable String name) {
        return ResponseEntity.ok(graph.detectConflictsForPerson(name));
    }

    @GetMapping("/persons/{name}/convictions")
    public ResponseEntity<?> convictions(@PathVariable String name) {
        return ResponseEntity.ok(graph.findConvictions(name));
    }

    @GetMapping("/persons/{name}/family")
    public ResponseEntity<?> family(@PathVariable String name) {
        return ResponseEntity.ok(graph.findFamilyNetwork(name));
    }

    @GetMapping("/companies/{name}/ubo")
    public ResponseEntity<?> ubo(@PathVariable String name) {
        return ResponseEntity.ok(graph.findUBO(name));
    }

    @GetMapping("/companies/{name}/ownership")
    public ResponseEntity<?> ownership(@PathVariable String name) {
        return ResponseEntity.ok(graph.findOwnershipChain(name));
    }

    @GetMapping("/companies/{name}/contracts")
    public ResponseEntity<?> contracts(@PathVariable String name) {
        return ResponseEntity.ok(graph.findContractsWonByCompany(name));
    }

    @GetMapping("/companies/{name}/taxhavens")
    public ResponseEntity<?> taxHavens(@PathVariable String name) {
        return ResponseEntity.ok(graph.findTaxHavenConnections(name));
    }

    @GetMapping("/path")
    public ResponseEntity<?> shortestPath(@RequestParam String from, @RequestParam String to) {
        if (from == null || to == null)
            return ResponseEntity.badRequest().body("'from' and 'to' query params required");
        var result = graph.shortestPath(from, to);
        return result != null ? ResponseEntity.ok(result) : ResponseEntity.notFound().build();
    }
}
