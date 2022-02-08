package de.cotto.bitbook.lnd;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.cotto.bitbook.backend.model.Transaction;
import de.cotto.bitbook.backend.model.TransactionHash;
import de.cotto.bitbook.backend.transaction.TransactionService;
import de.cotto.bitbook.lnd.model.Channel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.Set;

import static de.cotto.bitbook.backend.model.TransactionFixtures.TRANSACTION;
import static de.cotto.bitbook.lnd.model.ChannelFixtures.OPENING_TRANSACTION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChannelsParserTest {
    private static final TransactionHash HASH_A = new TransactionHash("a");
    private static final TransactionHash HASH_SOMEWHERE = new TransactionHash("somewhere");
    @InjectMocks
    private ChannelsParser channelsParser;

    @Mock
    private TransactionService transactionService;

    @Test
    void empty_json_object() throws IOException {
        assertThat(channelsParser.parse(toJsonNode("{}"))).isEmpty();
    }

    @Test
    void no_channels() throws IOException {
        assertThat(channelsParser.parse(toJsonNode("{\"foo\": 1}"))).isEmpty();
    }

    @Test
    void not_array() throws IOException {
        String json = "{\"channels\":1}";
        assertThat(channelsParser.parse(toJsonNode(json))).isEmpty();
    }

    @Test
    void parses_channel_and_gets_transaction_details() throws IOException {
        when(transactionService.getTransactionDetails(Set.of(HASH_SOMEWHERE))).thenReturn(Set.of());
        when(transactionService.getTransactionDetails(HASH_SOMEWHERE)).thenReturn(OPENING_TRANSACTION);
        String json = "{\"channels\":[" +
                      "{" +
                      "\"remote_pubkey\":\"pubkey\", " +
                      "\"initiator\": true," +
                      "\"channel_point\": \"somewhere:999\"" +
                      "}" +
                      "]}";
        assertThat(channelsParser.parse(toJsonNode(json)))
                .containsExactly(new Channel(true, "pubkey", OPENING_TRANSACTION, 999));
    }

    @Test
    void parses_many_channels() throws IOException {
        when(transactionService.getTransactionDetails(Set.of(HASH_A, new TransactionHash("b")))).thenReturn(Set.of());
        when(transactionService.getTransactionDetails(any(TransactionHash.class))).thenReturn(TRANSACTION);
        String json = "{\"channels\":[" +
                      "{\"remote_pubkey\":\"pubkey\", \"initiator\": true, \"channel_point\": \"a:1\"}," +
                      "{\"remote_pubkey\":\"pubkey2\", \"initiator\": false, \"channel_point\": \"b:2\"}" +
                      "]}";
        assertThat(channelsParser.parse(toJsonNode(json))).hasSize(2);
    }

    @Test
    void preloads_transaction_details() throws IOException {
        when(transactionService.getTransactionDetails(anySet())).thenReturn(Set.of());
        when(transactionService.getTransactionDetails(any(TransactionHash.class))).thenReturn(TRANSACTION);
        String json = "{\"channels\":[" +
                      "{\"remote_pubkey\":\"pubkey\", \"initiator\": true, \"channel_point\": \"a:1\"}," +
                      "{\"remote_pubkey\":\"pubkey2\", \"initiator\": false, \"channel_point\": \"b:2\"}" +
                      "]}";
        channelsParser.parse(toJsonNode(json));
        InOrder inOrder = Mockito.inOrder(transactionService);
        inOrder.verify(transactionService).getTransactionDetails(Set.of(HASH_A, new TransactionHash("b")));
        inOrder.verify(transactionService, times(2)).getTransactionDetails(any(TransactionHash.class));
    }

    @Test
    void ignores_channel_with_unknown_transaction() throws IOException {
        String json = "{\"channels\":[{\"remote_pubkey\":\"pubkey\", \"initiator\":true,\"channel_point\":\"a:1\"}]}";
        when(transactionService.getTransactionDetails(Set.of(HASH_A))).thenReturn(Set.of(Transaction.UNKNOWN));
        when(transactionService.getTransactionDetails(HASH_A)).thenReturn(Transaction.UNKNOWN);
        assertThat(channelsParser.parse(toJsonNode(json))).isEmpty();
    }

    private JsonNode toJsonNode(String json) throws IOException {
        try (JsonParser parser = new ObjectMapper().createParser(json)) {
            return parser.getCodec().readTree(parser);
        }
    }
}
