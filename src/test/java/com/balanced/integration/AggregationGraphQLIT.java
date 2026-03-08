package com.balanced.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AggregationGraphQLIT extends BaseIntegrationTest {

    private String token;

    @BeforeEach
    void setUp() {
        String email = "agg-%s@test.com".formatted(System.nanoTime());
        var bootstrap = bootstrapUser(email, "Agg", "User");
        token = (String) bootstrap.get("token");
    }

    @Test
    @SuppressWarnings("unchecked")
    void linkBank_createsConnectionAndAccounts() {
        String query = """
                mutation {
                    linkBank(input: { accessToken: "test_token_001" }) {
                        id provider enrollmentId institutionName status
                    }
                }
                """;
        var data = graphqlData(token, query);
        var connection = (Map<String, Object>) data.get("linkBank");

        assertThat(connection.get("provider")).isEqualTo("TELLER");
        assertThat(connection.get("enrollmentId")).isEqualTo("enr_mock_001");
        assertThat(connection.get("institutionName")).isEqualTo("Mock Bank");
        assertThat(connection.get("status")).isEqualTo("ACTIVE");

        String connectionId = (String) connection.get("id");

        // Verify accounts were created
        var accountsData = graphqlData(token, "{ accounts { id name source externalId bankConnectionId } }");
        var accounts = (List<Map<String, Object>>) accountsData.get("accounts");
        assertThat(accounts).hasSize(3);
        assertThat(accounts).allSatisfy(a -> {
            assertThat(a.get("source")).isEqualTo("AUTOMATIC");
            assertThat(a.get("bankConnectionId")).isEqualTo(connectionId);
            assertThat(a.get("externalId")).isNotNull();
        });
    }

    @Test
    @SuppressWarnings("unchecked")
    void bankConnections_returnsLinkedConnections() {
        // Link a bank first
        graphqlData(token, """
                mutation { linkBank(input: { accessToken: "token_list" }) { id } }
                """);

        // List connections
        var data = graphqlData(token, """
                { bankConnections { id provider institutionName status } }
                """);
        var connections = (List<Map<String, Object>>) data.get("bankConnections");

        assertThat(connections).hasSize(1);
        assertThat(connections.getFirst().get("provider")).isEqualTo("TELLER");
        assertThat(connections.getFirst().get("institutionName")).isEqualTo("Mock Bank");
    }

    @Test
    @SuppressWarnings("unchecked")
    void bankConnection_returnsSingleConnection() {
        var linkData = graphqlData(token, """
                mutation { linkBank(input: { accessToken: "token_single" }) { id } }
                """);
        String connectionId = (String) ((Map<String, Object>) linkData.get("linkBank")).get("id");

        var data = graphqlData(token, """
                query { bankConnection(bankConnectionId: "%s") { id provider enrollmentId institutionName status } }
                """.formatted(connectionId));
        var connection = (Map<String, Object>) data.get("bankConnection");

        assertThat(connection.get("id")).isEqualTo(connectionId);
        assertThat(connection.get("provider")).isEqualTo("TELLER");
    }

    @Test
    @SuppressWarnings("unchecked")
    void syncTransactions_importsTransactions() {
        // Link bank first
        var linkData = graphqlData(token, """
                mutation { linkBank(input: { accessToken: "token_sync" }) { id } }
                """);
        String connectionId = (String) ((Map<String, Object>) linkData.get("linkBank")).get("id");

        // Sync transactions
        var rawResponse = graphql(token, """
                mutation { syncTransactions(bankConnectionId: "%s") {
                    transactionsAdded transactionsModified transactionsRemoved accountsSynced
                } }
                """.formatted(connectionId));
        assertThat(rawResponse).as("Full GraphQL response").isNotNull();
        assertThat(rawResponse.get("errors")).as("GraphQL errors: %s", rawResponse.get("errors")).isNull();
        var syncData = (Map<String, Object>) rawResponse.get("data");
        assertThat(syncData).as("GraphQL data (full response: %s)", rawResponse).isNotNull();
        var result = (Map<String, Object>) syncData.get("syncTransactions");

        assertThat((Integer) result.get("transactionsAdded")).isGreaterThan(0);
        assertThat((Integer) result.get("accountsSynced")).isEqualTo(3);
    }

    @Test
    @SuppressWarnings("unchecked")
    void syncTransactions_skipsDuplicatesOnSecondSync() {
        var linkData = graphqlData(token, """
                mutation { linkBank(input: { accessToken: "token_dedup" }) { id } }
                """);
        String connectionId = (String) ((Map<String, Object>) linkData.get("linkBank")).get("id");

        // First sync
        var firstSync = graphqlData(token, """
                mutation { syncTransactions(bankConnectionId: "%s") { transactionsAdded } }
                """.formatted(connectionId));
        int firstAdded = (Integer) ((Map<String, Object>) firstSync.get("syncTransactions")).get("transactionsAdded");
        assertThat(firstAdded).isGreaterThan(0);

        // Second sync — should add 0
        var secondSync = graphqlData(token, """
                mutation { syncTransactions(bankConnectionId: "%s") { transactionsAdded } }
                """.formatted(connectionId));
        int secondAdded = (Integer) ((Map<String, Object>) secondSync.get("syncTransactions")).get("transactionsAdded");
        assertThat(secondAdded).isEqualTo(0);
    }

    @Test
    @SuppressWarnings("unchecked")
    void unlinkBank_closesConnectionAndConvertsAccounts() {
        var linkData = graphqlData(token, """
                mutation { linkBank(input: { accessToken: "token_unlink" }) { id } }
                """);
        String connectionId = (String) ((Map<String, Object>) linkData.get("linkBank")).get("id");

        // Unlink
        var unlinkData = graphqlData(token, """
                mutation { unlinkBank(bankConnectionId: "%s") }
                """.formatted(connectionId));
        assertThat(unlinkData.get("unlinkBank")).isEqualTo(true);

        // Verify connection is closed
        var connData = graphqlData(token, """
                query { bankConnection(bankConnectionId: "%s") { id status } }
                """.formatted(connectionId));
        var conn = (Map<String, Object>) connData.get("bankConnection");
        assertThat(conn.get("status")).isEqualTo("CLOSED");

        // Verify accounts converted to manual
        var accountsData = graphqlData(token, "{ accounts { id source bankConnectionId } }");
        var accounts = (List<Map<String, Object>>) accountsData.get("accounts");
        assertThat(accounts).allSatisfy(a -> {
            assertThat(a.get("source")).isEqualTo("MANUAL");
            assertThat(a.get("bankConnectionId")).isNull();
        });
    }

    @Test
    @SuppressWarnings("unchecked")
    void bankConnection_notFoundForOtherWorkspace() {
        var linkData = graphqlData(token, """
                mutation { linkBank(input: { accessToken: "token_isolation" }) { id } }
                """);
        String connectionId = (String) ((Map<String, Object>) linkData.get("linkBank")).get("id");

        // Create a different user
        String email2 = "agg-other-%s@test.com".formatted(System.nanoTime());
        var bootstrap2 = bootstrapUser(email2, "Other", "User");
        String token2 = (String) bootstrap2.get("token");

        // Should not find the connection from different workspace
        var result = graphql(token2, """
                query { bankConnection(bankConnectionId: "%s") { id } }
                """.formatted(connectionId));
        assertThat(result.get("errors")).isNotNull();
    }

}
