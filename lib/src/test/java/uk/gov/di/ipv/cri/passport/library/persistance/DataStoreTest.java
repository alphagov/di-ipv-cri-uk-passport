package uk.gov.di.ipv.cri.passport.library.persistance;

import com.nimbusds.oauth2.sdk.AuthorizationCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.pagination.sync.SdkIterable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.PageIterable;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import uk.gov.di.ipv.cri.passport.library.persistence.DataStore;
import uk.gov.di.ipv.cri.passport.library.persistence.item.AuthorizationCodeItem;
import uk.gov.di.ipv.cri.passport.library.service.ConfigurationService;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataStoreTest {
    private static final String TEST_TABLE_NAME = "test-auth-code-table";

    @Mock private DynamoDbEnhancedClient mockDynamoDbEnhancedClient;
    @Mock private DynamoDbTable<AuthorizationCodeItem> mockDynamoDbTable;
    @Mock private PageIterable<AuthorizationCodeItem> mockPageIterable;
    @Mock private DynamoDbIndex<AuthorizationCodeItem> mockIndex;
    @Mock private SdkIterable<Page<AuthorizationCodeItem>> mockIterable;
    @Mock private ConfigurationService mockConfigurationService;

    private AuthorizationCodeItem authorizationCodeItem;
    private DataStore<AuthorizationCodeItem> dataStore;

    @BeforeEach
    void setUp() {
        when(mockDynamoDbEnhancedClient.table(
                        anyString(), ArgumentMatchers.<TableSchema<AuthorizationCodeItem>>any()))
                .thenReturn(mockDynamoDbTable);

        authorizationCodeItem = new AuthorizationCodeItem();
        authorizationCodeItem.setAuthCode(new AuthorizationCode().getValue());
        authorizationCodeItem.setResourceId("test-resource-12345");

        dataStore =
                new DataStore<>(
                        TEST_TABLE_NAME,
                        AuthorizationCodeItem.class,
                        mockDynamoDbEnhancedClient,
                        mockConfigurationService);
    }

    @Test
    void shouldPutItemIntoDynamoDbTable() {
        dataStore.create(authorizationCodeItem);

        ArgumentCaptor<AuthorizationCodeItem> authorizationCodeItemArgumentCaptor =
                ArgumentCaptor.forClass(AuthorizationCodeItem.class);

        verify(mockDynamoDbEnhancedClient)
                .table(
                        eq(TEST_TABLE_NAME),
                        ArgumentMatchers.<TableSchema<AuthorizationCodeItem>>any());
        verify(mockDynamoDbTable).putItem(authorizationCodeItemArgumentCaptor.capture());
        assertEquals(
                authorizationCodeItem.getAuthCode(),
                authorizationCodeItemArgumentCaptor.getValue().getAuthCode());
        assertEquals(
                authorizationCodeItem.getResourceId(),
                authorizationCodeItemArgumentCaptor.getValue().getResourceId());
    }

    @Test
    void shouldGetItemFromDynamoDbTableViaPartitionKeyAndSortKey() {
        dataStore.getItem("partition-key-12345", "sort-key-12345");

        ArgumentCaptor<Key> keyCaptor = ArgumentCaptor.forClass(Key.class);

        verify(mockDynamoDbEnhancedClient)
                .table(
                        eq(TEST_TABLE_NAME),
                        ArgumentMatchers.<TableSchema<AuthorizationCodeItem>>any());
        verify(mockDynamoDbTable).getItem(keyCaptor.capture());
        assertEquals("partition-key-12345", keyCaptor.getValue().partitionKeyValue().s());
        assertEquals("sort-key-12345", keyCaptor.getValue().sortKeyValue().get().s());
    }

    @Test
    void shouldGetItemFromDynamoDbTableViaPartitionKey() {
        dataStore.getItem("partition-key-12345");

        ArgumentCaptor<Key> keyCaptor = ArgumentCaptor.forClass(Key.class);

        verify(mockDynamoDbEnhancedClient)
                .table(
                        eq(TEST_TABLE_NAME),
                        ArgumentMatchers.<TableSchema<AuthorizationCodeItem>>any());
        verify(mockDynamoDbTable).getItem(keyCaptor.capture());
        assertEquals("partition-key-12345", keyCaptor.getValue().partitionKeyValue().s());
        assertTrue(keyCaptor.getValue().sortKeyValue().isEmpty());
    }

    @Test
    void shouldGetItemFromDynamoDbTableViaIndex() {
        when(mockIndex.query((QueryEnhancedRequest) any())).thenReturn(mockIterable);
        when(mockDynamoDbTable.index(anyString())).thenReturn(mockIndex);

        String indexName = "test-index";
        dataStore.getItemByIndex(indexName, "partition-key-12345");

        verify(mockIndex).query(any(QueryEnhancedRequest.class));
    }

    @Test
    void shouldGetItemsFromDynamoDbTableViaPartitionKeyQueryRequest() {
        when(mockDynamoDbTable.query(any(QueryConditional.class))).thenReturn(mockPageIterable);
        when(mockPageIterable.stream()).thenReturn(Stream.empty());

        dataStore.getItems("partition-key-12345");

        verify(mockDynamoDbEnhancedClient)
                .table(
                        eq(TEST_TABLE_NAME),
                        ArgumentMatchers.<TableSchema<AuthorizationCodeItem>>any());
        verify(mockDynamoDbTable).query(any(QueryConditional.class));
    }

    @Test
    void shouldUpdateItemInDynamoDbTable() {
        dataStore.update(authorizationCodeItem);

        ArgumentCaptor<AuthorizationCodeItem> authorizationCodeItemArgumentCaptor =
                ArgumentCaptor.forClass(AuthorizationCodeItem.class);

        verify(mockDynamoDbEnhancedClient)
                .table(
                        eq(TEST_TABLE_NAME),
                        ArgumentMatchers.<TableSchema<AuthorizationCodeItem>>any());
        verify(mockDynamoDbTable).updateItem(authorizationCodeItemArgumentCaptor.capture());
        assertEquals(
                authorizationCodeItem.getAuthCode(),
                authorizationCodeItemArgumentCaptor.getValue().getAuthCode());
        assertEquals(
                authorizationCodeItem.getResourceId(),
                authorizationCodeItemArgumentCaptor.getValue().getResourceId());
    }

    @Test
    void shouldDeleteItemFromDynamoDbTableViaPartitionKeyAndSortKey() {
        dataStore.delete("partition-key-12345", "sort-key-12345");

        ArgumentCaptor<Key> keyCaptor = ArgumentCaptor.forClass(Key.class);

        verify(mockDynamoDbEnhancedClient)
                .table(
                        eq(TEST_TABLE_NAME),
                        ArgumentMatchers.<TableSchema<AuthorizationCodeItem>>any());
        verify(mockDynamoDbTable).deleteItem(keyCaptor.capture());
        assertEquals("partition-key-12345", keyCaptor.getValue().partitionKeyValue().s());
        assertEquals("sort-key-12345", keyCaptor.getValue().sortKeyValue().get().s());
    }

    @Test
    void shouldDeleteItemFromDynamoDbTableViaPartitionKey() {
        dataStore.delete("partition-key-12345");

        ArgumentCaptor<Key> keyCaptor = ArgumentCaptor.forClass(Key.class);

        verify(mockDynamoDbEnhancedClient)
                .table(
                        eq(TEST_TABLE_NAME),
                        ArgumentMatchers.<TableSchema<AuthorizationCodeItem>>any());
        verify(mockDynamoDbTable).deleteItem(keyCaptor.capture());
        assertEquals("partition-key-12345", keyCaptor.getValue().partitionKeyValue().s());
        assertTrue(keyCaptor.getValue().sortKeyValue().isEmpty());
    }
}
