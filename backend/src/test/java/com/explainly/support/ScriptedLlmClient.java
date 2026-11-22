package com.explainly.support;

import com.explainly.ai.LlmClient;
import com.explainly.ai.LlmRequest;
import com.explainly.ai.LlmResponse;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Deterministic test double for an LLM provider: returns well-formed responses per request purpose. */
public class ScriptedLlmClient implements LlmClient {

    public final List<LlmRequest> requests = new CopyOnWriteArrayList<>();

    @Override public String id() { return "scripted"; }
    @Override public String model() { return "scripted-model"; }
    @Override public boolean isConfigured() { return true; }

    @Override
    public LlmResponse complete(LlmRequest request) {
        requests.add(request);
        String text = switch (request.purpose()) {
            case "structure" -> """
                    Here is the analysis:
                    ```json
                    {"category":"Programming","language":"en",
                     "overview":{"title":"How Spring Boot handles a request","whatIsItAbout":"A request flows through layers.",
                       "whatYouWillLearn":["Controllers","Services"],"whyItMatters":"Every web app does this.",
                       "whoShouldWatch":"New Java developers","difficulty":"Beginner","keyTakeaways":["Layers keep code tidy"]},
                     "chapters":[
                       {"title":"Controllers","startSeconds":0,"endSeconds":30,"shortSummary":"Entry point","simpleExplanation":"The controller receives the request.",
                        "detailedExplanation":"**Controllers** map URLs.","realWorldExample":"A receptionist","practicalUseCase":"REST APIs",
                        "keyPoints":["Annotated with @RestController"],"concepts":["Controller"]},
                       {"title":"Services and repositories","startSeconds":"0:30","endSeconds":null,"shortSummary":"Business logic","simpleExplanation":"Services do the work.",
                        "detailedExplanation":"Repositories talk to the database.","realWorldExample":"A kitchen","practicalUseCase":"Saving orders",
                        "keyPoints":["Keep logic in services"],"concepts":["Service","Repository"]}],
                     "concepts":[{"term":"Controller","kind":"DEFINITION","definition":"Handles HTTP requests","simpleExplanation":"The front desk","timestampSeconds":5},
                                 {"term":"Repository","kind":"weird","definition":"Data access","simpleExplanation":"The storage room","timestampSeconds":40}]}
                    ```""";
            case "learning-modes" -> """
                    {"beginnerExplanation":"# Beginner\\nThink of a restaurant.","technicalExplanation":"# Technical\\nDispatcherServlet routes requests.",
                     "summaries":{"oneMinute":"Short summary.","fiveMinute":"## Medium\\nLonger summary.","detailed":"## Detailed\\nFull summary."},
                     "notes":"# Notes\\n## Controllers (0:00)\\n- Receive requests\\n- **Map** URLs\\n```java\\n@GetMapping\\n```\\n## Key terms\\n- Controller"}""";
            case "illustrations" -> """
                    {"illustrations":[
                      {"chapterIndex":0,"title":"Request flow","type":"architecture","explanation":"Shows layers","example":"GET /orders",
                       "nodes":[{"id":"c","label":"Controller","layer":0},{"id":"s","label":"Service","layer":1},{"id":"r","label":"Repository","layer":2},{"id":"d","label":"Database <script>","layer":3}],
                       "edges":[{"from":"c","to":"s"},{"from":"s","to":"r"},{"from":"r","to":"d","label":"SQL"},{"from":"x","to":"c"}]},
                      {"chapterIndex":null,"title":"Big picture","type":"mindmap","explanation":"All ideas","example":"",
                       "nodes":[{"id":"m","label":"Spring Boot"},{"id":"a","label":"Web"},{"id":"b","label":"Data"}],"edges":[{"from":"m","to":"a"},{"from":"m","to":"b"}]},
                      {"chapterIndex":7,"title":"Bad","type":"comparison","columns":[{"title":"Only one","items":["x"]}]}]}""";
            case "chat" -> "Controllers receive requests first [0:05]. Then services run [0:30].";
            case "quiz" -> """
                    {"title":"Spring Boot basics","questions":[
                      {"type":"MULTIPLE_CHOICE","question":"What receives the request?","options":["Controller","Database","Repository","Service"],"correctIndex":0,"explanation":"The controller is the entry point.","timestampSeconds":5},
                      {"type":"TRUE_FALSE","question":"Repositories hold business logic.","options":["True","False"],"correctIndex":1,"explanation":"Services do.","timestampSeconds":30},
                      {"type":"CODE","question":"Which annotation maps GET?","codeSnippet":"@???(\\"/orders\\")","options":["@GetMapping","@Get","@Route","@Map"],"correctIndex":0,"explanation":"@GetMapping."},
                      {"type":"MULTIPLE_CHOICE","question":"Invalid","options":["a"],"correctIndex":3}]}""";
            case "flashcards" -> """
                    {"flashcards":[{"question":"What is a controller?","answer":"Handles requests","definition":"Entry point"},
                     {"question":"What is a service?","answer":"Business logic","definition":null},
                     {"question":"What is a repository?","answer":"Data access","definition":"Talks to the DB"}]}""";
            default -> "Condensed notes.";
        };
        return new LlmResponse(text, id(), model(), false);
    }
}
