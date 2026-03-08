package com.balanced.aggregation;

import com.balanced.aggregation.dto.BankConnectionResponse;
import com.balanced.aggregation.dto.LinkBankInput;
import com.balanced.aggregation.dto.SyncResult;
import com.balanced.aggregation.entity.BankConnection;
import com.balanced.aggregation.enums.AggregationProvider;
import com.balanced.aggregation.mapper.BankConnectionMapper;
import com.balanced.aggregation.resolver.AggregationResolver;
import com.balanced.aggregation.service.AggregationService;
import com.balanced.common.enums.Status;
import com.balanced.common.graphql.GraphQLContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AggregationResolverTest {

    @Mock private AggregationService aggregationService;
    @Mock private BankConnectionMapper bankConnectionMapper;

    @InjectMocks private AggregationResolver aggregationResolver;

    private static final UUID WORKSPACE_ID = UUID.randomUUID();
    private static final UUID CONNECTION_ID = UUID.randomUUID();

    private MockedStatic<GraphQLContext> graphQLContextMock;

    @BeforeEach
    void setUp() {
        graphQLContextMock = mockStatic(GraphQLContext.class);
        graphQLContextMock.when(GraphQLContext::workspaceId).thenReturn(WORKSPACE_ID);
    }

    @AfterEach
    void tearDown() {
        graphQLContextMock.close();
    }

    private BankConnection mockConnection() {
        return BankConnection.builder()
                .id(CONNECTION_ID)
                .workspaceId(WORKSPACE_ID)
                .provider(AggregationProvider.TELLER)
                .enrollmentId("enr_001")
                .institutionName("Test Bank")
                .status(Status.ACTIVE)
                .build();
    }

    private BankConnectionResponse mockResponse() {
        return BankConnectionResponse.builder()
                .id(CONNECTION_ID)
                .workspaceId(WORKSPACE_ID)
                .provider(AggregationProvider.TELLER)
                .enrollmentId("enr_001")
                .institutionName("Test Bank")
                .status(Status.ACTIVE)
                .build();
    }

    @Nested
    class BankConnections {

        @Test
        void returnsMappedConnections() {
            var connection = mockConnection();
            var response = mockResponse();
            when(aggregationService.listConnections(WORKSPACE_ID)).thenReturn(List.of(connection));
            when(bankConnectionMapper.toDtos(List.of(connection))).thenReturn(List.of(response));

            List<BankConnectionResponse> result = aggregationResolver.bankConnections();

            assertThat(result).hasSize(1);
            assertThat(result.getFirst().getInstitutionName()).isEqualTo("Test Bank");
        }
    }

    @Nested
    class BankConnectionQuery {

        @Test
        void returnsMappedConnection() {
            var connection = mockConnection();
            var response = mockResponse();
            when(aggregationService.getConnection(CONNECTION_ID, WORKSPACE_ID)).thenReturn(connection);
            when(bankConnectionMapper.toDto(connection)).thenReturn(response);

            BankConnectionResponse result = aggregationResolver.bankConnection(CONNECTION_ID);

            assertThat(result.getId()).isEqualTo(CONNECTION_ID);
            assertThat(result.getProvider()).isEqualTo(AggregationProvider.TELLER);
        }
    }

    @Nested
    class LinkBank {

        @Test
        void linksBank() {
            var input = new LinkBankInput("token_123");
            var connection = mockConnection();
            var response = mockResponse();
            when(aggregationService.linkBank(WORKSPACE_ID, "token_123"))
                    .thenReturn(connection);
            when(bankConnectionMapper.toDto(connection)).thenReturn(response);

            BankConnectionResponse result = aggregationResolver.linkBank(input);

            assertThat(result.getInstitutionName()).isEqualTo("Test Bank");
            verify(aggregationService).linkBank(WORKSPACE_ID, "token_123");
        }
    }

    @Nested
    class SyncTransactionsResolver {

        @Test
        void returnsSyncResult() {
            var syncResult = new SyncResult(5, 0, 0, 2);
            when(aggregationService.syncTransactions(CONNECTION_ID, WORKSPACE_ID)).thenReturn(syncResult);

            SyncResult result = aggregationResolver.syncTransactions(CONNECTION_ID);

            assertThat(result.transactionsAdded()).isEqualTo(5);
            assertThat(result.accountsSynced()).isEqualTo(2);
        }
    }

    @Nested
    class UnlinkBankResolver {

        @Test
        void returnsTrue() {
            boolean result = aggregationResolver.unlinkBank(CONNECTION_ID);

            assertThat(result).isTrue();
            verify(aggregationService).unlinkBank(CONNECTION_ID, WORKSPACE_ID);
        }
    }
}
