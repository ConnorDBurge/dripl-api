package com.balanced.aggregation;

import com.balanced.account.enums.AccountSubType;
import com.balanced.account.enums.AccountType;
import com.balanced.aggregation.mapper.AggregatedAccountTypeMapper;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AggregatedAccountTypeMapperTest {

    @Nested
    class MapTellerType {

        @Test
        void depositoryCheckingMapsToCash() {
            assertThat(AggregatedAccountTypeMapper.mapTellerType("depository", "checking"))
                    .isEqualTo(AccountType.CASH);
        }

        @Test
        void depositorySavingsMapsToCash() {
            assertThat(AggregatedAccountTypeMapper.mapTellerType("depository", "savings"))
                    .isEqualTo(AccountType.CASH);
        }

        @Test
        void depositoryMoneyMarketMapsToCash() {
            assertThat(AggregatedAccountTypeMapper.mapTellerType("depository", "money_market"))
                    .isEqualTo(AccountType.CASH);
        }

        @Test
        void depositoryCdMapsToCash() {
            assertThat(AggregatedAccountTypeMapper.mapTellerType("depository", "certificate_of_deposit"))
                    .isEqualTo(AccountType.CASH);
        }

        @Test
        void depositoryTreasuryMapsToCash() {
            assertThat(AggregatedAccountTypeMapper.mapTellerType("depository", "treasury"))
                    .isEqualTo(AccountType.CASH);
        }

        @Test
        void depositorySweepMapsToCash() {
            assertThat(AggregatedAccountTypeMapper.mapTellerType("depository", "sweep"))
                    .isEqualTo(AccountType.CASH);
        }

        @Test
        void creditCardMapsToCredit() {
            assertThat(AggregatedAccountTypeMapper.mapTellerType("credit", "credit_card"))
                    .isEqualTo(AccountType.CREDIT);
        }

        @Test
        void unknownTypeDefaultsToCash() {
            assertThat(AggregatedAccountTypeMapper.mapTellerType("unknown", "unknown"))
                    .isEqualTo(AccountType.CASH);
        }
    }

    @Nested
    class MapTellerSubType {

        @Test
        void checkingMapsToChecking() {
            assertThat(AggregatedAccountTypeMapper.mapTellerSubType("depository", "checking"))
                    .isEqualTo(AccountSubType.CHECKING);
        }

        @Test
        void savingsMapsToSavings() {
            assertThat(AggregatedAccountTypeMapper.mapTellerSubType("depository", "savings"))
                    .isEqualTo(AccountSubType.SAVINGS);
        }

        @Test
        void moneyMarketMapsToSavings() {
            assertThat(AggregatedAccountTypeMapper.mapTellerSubType("depository", "money_market"))
                    .isEqualTo(AccountSubType.SAVINGS);
        }

        @Test
        void cdMapsToOtherCash() {
            assertThat(AggregatedAccountTypeMapper.mapTellerSubType("depository", "certificate_of_deposit"))
                    .isEqualTo(AccountSubType.OTHER_CASH);
        }

        @Test
        void treasuryMapsToOtherCash() {
            assertThat(AggregatedAccountTypeMapper.mapTellerSubType("depository", "treasury"))
                    .isEqualTo(AccountSubType.OTHER_CASH);
        }

        @Test
        void sweepMapsToOtherCash() {
            assertThat(AggregatedAccountTypeMapper.mapTellerSubType("depository", "sweep"))
                    .isEqualTo(AccountSubType.OTHER_CASH);
        }

        @Test
        void creditCardMapsToCreditCard() {
            assertThat(AggregatedAccountTypeMapper.mapTellerSubType("credit", "credit_card"))
                    .isEqualTo(AccountSubType.CREDIT_CARD);
        }

        @Test
        void unknownSubTypeDefaultsToOtherCash() {
            assertThat(AggregatedAccountTypeMapper.mapTellerSubType("unknown", "unknown"))
                    .isEqualTo(AccountSubType.OTHER_CASH);
        }
    }
}
