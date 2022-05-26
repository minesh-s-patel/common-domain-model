package org.isda.cdm.functions;

import cdm.base.math.FinancialUnitEnum;
import cdm.base.math.Quantity;
import cdm.base.math.QuantityChangeDirectionEnum;
import cdm.base.math.UnitType;
import cdm.base.math.metafields.FieldWithMetaQuantity;
import cdm.base.staticdata.identifier.Identifier;
import cdm.base.staticdata.party.*;
import cdm.base.staticdata.party.metafields.ReferenceWithMetaParty;
import cdm.event.common.*;
import cdm.event.common.functions.Create_BusinessEvent;
import cdm.event.common.functions.Create_Execution;
import cdm.event.common.metafields.ReferenceWithMetaTradeState;
import cdm.event.position.PositionStatusEnum;
import cdm.event.workflow.Workflow;
import cdm.event.workflow.WorkflowStep;
import cdm.legalagreement.common.ClosedState;
import cdm.legalagreement.common.ClosedStateEnum;
import cdm.observable.asset.Price;
import cdm.observable.event.Observation;
import cdm.observable.event.ObservationIdentifier;
import cdm.product.common.settlement.PriceQuantity;
import cdm.product.template.TradeLot;
import cdm.security.lending.functions.RunNewSettlementWorkflow;
import cdm.security.lending.functions.RunReturnSettlementWorkflow;
import cdm.security.lending.functions.RunReturnSettlementWorkflowInput;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.Lists;
import com.google.common.io.Resources;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.util.Modules;
import com.regnosys.rosetta.common.serialisation.RosettaObjectMapper;
import com.rosetta.model.lib.process.PostProcessor;
import com.rosetta.model.lib.records.Date;
import com.rosetta.model.lib.validation.ModelObjectValidator;
import com.rosetta.model.metafields.FieldWithMetaString;
import com.rosetta.model.metafields.MetaFields;
import org.isda.cdm.CdmRuntimeModule;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.ResourcesUtils;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SecLendingFunctionInputCreationTest {

    private static final boolean WRITE_EXPECTATIONS =
            Optional.ofNullable(System.getenv("WRITE_EXPECTATIONS"))
                    .map(Boolean::parseBoolean).orElse(false);
    private static final Optional<Path> TEST_WRITE_BASE_PATH =
            Optional.ofNullable(System.getenv("TEST_WRITE_BASE_PATH")).map(Paths::get);
    private static final Logger LOGGER = LoggerFactory.getLogger(SecLendingFunctionInputCreationTest.class);


    private static final ObjectMapper STRICT_MAPPER = RosettaObjectMapper.getNewRosettaObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true)
            .configure(JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN, true)
            .setNodeFactory(JsonNodeFactory.withExactBigDecimals(true));

    public static final ObjectMapper MAPPER = RosettaObjectMapper.getNewRosettaObjectMapper();

    // AVOID ADDING MANUALLY CRAFTED JSON

    // ALLOCATION AND REALLOCATION EXAMPLES ARE BASED ON THIS EXECUTION INSTRUCTION.
    // This is the execution instruction between an agent lender and a borrower
    public static final String EXECUTION_INSTRUCTION_JSON = "/cdm-sample-files/functions/sec-lending/block-execution-instruction.json";

    // SETTLEMENT AND RETURN WORKFLOWS ARE BASED OF THIS..
    public static final String SETTLEMENT_WORKFLOW_FUNC_INPUT_JSON = "/cdm-sample-files/functions/sec-lending/new-settlement-workflow-func-input.json";

    private static Injector injector;

    @BeforeAll
    static void setup() {
        Module module = Modules.override(new CdmRuntimeModule())
                .with(new AbstractModule() {
                    @Override
                    protected void configure() {
                        bind(PostProcessor.class).to(GlobalKeyProcessRunner.class);
                        bind(ModelObjectValidator.class).toInstance(Mockito.mock(ModelObjectValidator.class));
                    }
                });
        injector = Guice.createInjector(module);
    }

    @Test
    void validateNewSettlementWorkflowFuncInputJson() throws IOException {
        assertJsonConformsToRosettaType(SETTLEMENT_WORKFLOW_FUNC_INPUT_JSON, ExecutionInstruction.class);
    }

    @Test
    void validateExecutionInstructionWorkflowFuncInputJson() throws IOException {
        assertJsonConformsToRosettaType(EXECUTION_INSTRUCTION_JSON, ExecutionInstruction.class);
    }

    @Test
    void validatePartReturnSettlementWorkflowFuncInputJson() throws IOException {
        assertJsonConformsToRosettaType("/cdm-sample-files/functions/sec-lending/part-return-settlement-workflow-func-input.json", RunReturnSettlementWorkflowInput.class);

        RunReturnSettlementWorkflowInput actual = new RunReturnSettlementWorkflowInput(getTransferTradeState(),
                ReturnInstruction.builder()
                        .addQuantity(Quantity.builder()
                                .setAmount(BigDecimal.valueOf(50000))
                                .setUnitOfAmount(UnitType.builder().setFinancialUnit(FinancialUnitEnum.SHARE).build())
                                .build())
                        .addQuantity(Quantity.builder()
                                .setAmount(BigDecimal.valueOf(1250000))
                                .setUnitOfAmount(UnitType.builder()
                                        .setCurrency(FieldWithMetaString.builder().setValue("USD").build()).build())
                                .build())
                        .build(),
                Date.of(2020, 10, 8)
        );

        assertEquals(readResource("/cdm-sample-files/functions/sec-lending/part-return-settlement-workflow-func-input.json"),
                STRICT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(actual),
                "The input JSON for part-return-settlement-workflow-func-input.json has been updated (probably due to a model change). Update the input file");
    }

    @Test
    void validateFullReturnSettlementWorkflowFuncInputJson() throws IOException {
        assertJsonConformsToRosettaType("/cdm-sample-files/functions/sec-lending/full-return-settlement-workflow-func-input.json", RunReturnSettlementWorkflowInput.class);

        ReturnInstruction returnInstruction = ReturnInstruction.builder()
                .addQuantity(Quantity.builder()
                        .setAmount(BigDecimal.valueOf(200000))
                        .setUnitOfAmount(UnitType.builder().setFinancialUnit(FinancialUnitEnum.SHARE)))
                .addQuantity(Quantity.builder()
                        .setAmount(BigDecimal.valueOf(5000000))
                        .setUnitOfAmount(UnitType.builder().setCurrencyValue("USD")))
                .build();

        RunReturnSettlementWorkflowInput actual = new RunReturnSettlementWorkflowInput(getTransferTradeState(),
                returnInstruction,
                Date.of(2020, 10, 21));

        assertEquals(readResource("/cdm-sample-files/functions/sec-lending/full-return-settlement-workflow-func-input.json"),
                STRICT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(actual),
                "The input JSON for full-return-settlement-workflow-func-input.json has been updated (probably due to a model change). Update the input file");
    }

    @Test
    void validateCreateAllocationFuncInputJson() throws IOException {
        // Agent Lender lends 200k SDOL to Borrower CP001
        TradeState blockExecutionTradeState = getBlockExecutionTradeState();

        SplitInstruction splitInstruction = SplitInstruction.builder()
                // Fund 1 lends 80k SDOL to Borrower CP001
                .addBreakdown(createAllocationInstruction(blockExecutionTradeState, "lender-1", "Fund 1", 0.40))
                // Fund 2 lends 120k SDOL to Borrower CP001
                .addBreakdown(createAllocationInstruction(blockExecutionTradeState, "lender-2", "Fund 2", 0.60))
                // Close original trade
                .addBreakdown(PrimitiveInstruction.builder()
                        .setQuantityChange(QuantityChangeInstruction.builder()
                                .setDirection(QuantityChangeDirectionEnum.REPLACE)
                                .addChange(PriceQuantity.builder()
                                        .addQuantityValue(Quantity.builder()
                                                .setAmount(BigDecimal.valueOf(0.0))
                                                .setUnitOfAmount(UnitType.builder()
                                                        .setCurrencyValue("USD")))
                                        .addQuantityValue(Quantity.builder()
                                                .setAmount(BigDecimal.valueOf(0.0))
                                                .setUnitOfAmount(UnitType.builder()
                                                        .setFinancialUnit(FinancialUnitEnum.SHARE))))));

        Instruction.InstructionBuilder instruction = Instruction.builder()
                .setBeforeValue(blockExecutionTradeState)
                .setPrimitiveInstruction(PrimitiveInstruction.builder().setSplit(splitInstruction));

        ResourcesUtils.reKey(instruction);

        CreateBusinessEventWorkflowInput actual = new CreateBusinessEventWorkflowInput(
                Lists.newArrayList(instruction.build()),
                EventIntentEnum.ALLOCATION,
                Date.of(2020, 9, 21),
                null);

        assertJsonEquals("cdm-sample-files/functions/sec-lending/allocation/allocation-sec-lending-func-input.json", actual);
    }

    @Test
    void validateCreateReallocationFuncInputJson() throws IOException {
        // Agent Lender lends 200k SDOL to Borrower CP001
        TradeState blockExecutionTradeState = getBlockExecutionTradeState();

        SplitInstruction splitInstruction = SplitInstruction.builder()
                // Fund 1 lends 120k SDOL to Borrower CP001
                .addBreakdown(createAllocationInstruction(blockExecutionTradeState, "lender-1", "Fund 1", 0.60))
                // Fund 2 lends 80k SDOL to Borrower CP001
                .addBreakdown(createAllocationInstruction(blockExecutionTradeState, "lender-2", "Fund 2", 0.40))
                // Close original trade
                .addBreakdown(PrimitiveInstruction.builder()
                        .setQuantityChange(QuantityChangeInstruction.builder()
                                .setDirection(QuantityChangeDirectionEnum.REPLACE)
                                .addChange(PriceQuantity.builder()
                                        .addQuantityValue(Quantity.builder()
                                                .setAmount(BigDecimal.valueOf(0.0))
                                                .setUnitOfAmount(UnitType.builder()
                                                        .setCurrencyValue("USD")))
                                        .addQuantityValue(Quantity.builder()
                                                .setAmount(BigDecimal.valueOf(0.0))
                                                .setUnitOfAmount(UnitType.builder()
                                                        .setFinancialUnit(FinancialUnitEnum.SHARE))))));

        Instruction.InstructionBuilder instruction = Instruction.builder()
                .setBeforeValue(blockExecutionTradeState)
                .setPrimitiveInstruction(PrimitiveInstruction.builder().setSplit(splitInstruction));

        ResourcesUtils.reKey(instruction);

        // we want to get the contract formation for the 40% allocated trade, and back it out by 25% causing a decrease quantity change (so notional will be 10% of the original block)
        // we then want to do a new allocation of 10% of the original block.
        BusinessEvent originalAllocationBusinessEvent = injector.getInstance(Create_BusinessEvent.class)
                .evaluate(Lists.newArrayList(instruction.build()),
                        EventIntentEnum.ALLOCATION,
                        Date.of(2020, 9, 21),
                        null);

        TradeState closedBlockTradeState = originalAllocationBusinessEvent.getAfter().stream()
                .filter(x -> Optional.ofNullable(x.getState()).map(State::getClosedState).map(ClosedState::getState).map(ClosedStateEnum.TERMINATED::equals).orElse(false))
                .filter(x -> Optional.ofNullable(x.getState()).map(State::getPositionState).map(PositionStatusEnum.CLOSED::equals).orElse(false))
                .findFirst()
                .orElseThrow(RuntimeException::new);

        // Trade state where the quantity is 40%
        TradeState tradeStateToBeDecreased = originalAllocationBusinessEvent.getAfter().get(1);

        AllocationBreakdown.AllocationBreakdownBuilder reallocationBreakdown = createAllocationBreakdown(blockExecutionTradeState, "lender-3", "Fund 3", 0.10);
        ReallocationInstruction actualReallocationInstruction = ReallocationInstruction.builder()
                // Fund 3 lends 20k SDOL to Borrower CP001
                .addBreakdowns(reallocationBreakdown.build())
                // Fund 2 lends 60k SDOL to Borrower CP001
                .addDecrease(DecreasedTrade.builder()
                        .setTradeState(tradeStateToBeDecreased)
                        .setQuantity(scaleQuantities(tradeStateToBeDecreased, 0.75))
                        .build())
                .build();

        JsonNode expectedJsonNode = STRICT_MAPPER
                .readTree(this.getClass()
                        .getResource("/cdm-sample-files/functions/sec-lending/create-reallocation-pre-settled-func-input.json"));

        TradeState expectedTradeState = assertJsonConformsToRosettaType(expectedJsonNode
                .get("originalBlock"), TradeState.class);
        ReallocationInstruction expectedReallocationInstruction = assertJsonConformsToRosettaType(expectedJsonNode
                .get("reallocationInstruction"), ReallocationInstruction.class);

        assertEquals(STRICT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(asJsonNode(Stream
						.of(new AbstractMap.SimpleEntry<>("originalBlock", expectedTradeState),
								new AbstractMap.SimpleEntry<>("reallocationInstruction", expectedReallocationInstruction))
						.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)))),
                STRICT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(asJsonNode(Stream
                        .of(new AbstractMap.SimpleEntry<>("originalBlock", closedBlockTradeState),
                                new AbstractMap.SimpleEntry<>("reallocationInstruction", actualReallocationInstruction)
                        )
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)))),
                "The input JSON for create-reallocation-pre-settled-func-input.json has been updated (probably due to a model change). Update the input file");
    }

    @Test
    void validateCreateSecurityLendingInvoiceFuncInputJson() throws IOException {
        RunReturnSettlementWorkflowInput input = assertJsonConformsToRosettaType("/cdm-sample-files/functions/sec-lending/part-return-settlement-workflow-func-input.json", RunReturnSettlementWorkflowInput.class);
        Workflow part = injector.getInstance(RunReturnSettlementWorkflow.class).execute(input);

        TradeState fullReturnAfterTradeState = getTransferTradeState();

		TradeState partReturnBeforeTradeState = part.getSteps().stream()
                .map(WorkflowStep::getBusinessEvent).filter(Objects::nonNull)
                .map(BusinessEvent::getPrimitives).flatMap(Collection::stream).filter(Objects::nonNull)
                .map(PrimitiveEvent::getTransfer).filter(Objects::nonNull)
                .map(TransferPrimitive::getBefore).filter(Objects::nonNull)
                .map(ReferenceWithMetaTradeState::getValue)
                .findFirst().orElseThrow(RuntimeException::new);

        TradeState partReturnAfterTradeState = part.getSteps().stream()
                .map(WorkflowStep::getBusinessEvent).filter(Objects::nonNull)
                .map(BusinessEvent::getPrimitives).flatMap(Collection::stream).filter(Objects::nonNull)
                .map(PrimitiveEvent::getTransfer).filter(Objects::nonNull)
                .map(TransferPrimitive::getAfter).filter(Objects::nonNull)
                .findFirst().orElseThrow(RuntimeException::new);

        BillingInstruction actualBillingInstruction = BillingInstruction.builder()
                .setSendingParty(getParty(partReturnAfterTradeState, CounterpartyRoleEnum.PARTY_1))
                .setReceivingParty(getParty(partReturnAfterTradeState, CounterpartyRoleEnum.PARTY_2))
                .setBillingStartDate(Date.of(2020, 10, 1))
                .setBillingEndDate(Date.of(2020, 10, 31))

                .addBillingRecordInstruction(createBillingRecordInstruction(fullReturnAfterTradeState,
                        Date.of(2020, 10, 1),
                        Date.of(2020, 10, 22),
                        Date.of(2020, 11, 10), Arrays.asList(
                                obs("2020-10-22", 28.18, "USD"),
                                obs("2020-10-21", 28.34, "USD"),
                                obs("2020-10-20", 30.72, "USD"),
                                obs("2020-10-19", 32.01, "USD"),
                                obs("2020-10-18", 32.12, "USD"),
                                obs("2020-10-17", 32.12, "USD"),
                                obs("2020-10-16", 32.12, "USD"),
                                obs("2020-10-15", 31.46, "USD"),
                                obs("2020-10-14", 31.93, "USD"),
                                obs("2020-10-13", 31.87, "USD"),
                                obs("2020-10-12", 31.13, "USD"),
                                obs("2020-10-11", 30.03, "USD"),
                                obs("2020-10-10", 30.03, "USD"),
                                obs("2020-10-09", 30.03, "USD"),
                                obs("2020-10-08", 29.53, "USD"),
                                obs("2020-10-07", 28.72, "USD"),
                                obs("2020-10-06", 28.04, "USD"),
                                obs("2020-10-05", 27.67, "USD"),
                                obs("2020-10-04", 27.23, "USD"),
                                obs("2020-10-03", 27.23, "USD"),
                                obs("2020-10-02", 27.23, "USD"),
                                obs("2020-10-01", 26.87, "USD")
                        )))
                .addBillingRecordInstruction(createBillingRecordInstruction(partReturnBeforeTradeState,
                        Date.of(2020, 10, 1),
                        Date.of(2020, 10, 9),
                        Date.of(2020, 11, 10), Arrays.asList(
                                obs("2020-10-09", 30.03, "USD"),
                                obs("2020-10-08", 29.53, "USD"),
                                obs("2020-10-07", 28.72, "USD"),
                                obs("2020-10-06", 28.04, "USD"),
                                obs("2020-10-05", 27.67, "USD"),
                                obs("2020-10-04", 27.23, "USD"),
                                obs("2020-10-03", 27.23, "USD"),
                                obs("2020-10-02", 27.23, "USD"),
                                obs("2020-10-01", 26.87, "USD")
                        )))
                .addBillingRecordInstruction(createBillingRecordInstruction(partReturnAfterTradeState,
                        Date.of(2020, 10, 10),
                        Date.of(2020, 10, 22),
                        Date.of(2020, 11, 10), Arrays.asList(
                                obs("2020-10-22", 28.18, "USD"),
                                obs("2020-10-21", 28.34, "USD"),
                                obs("2020-10-20", 30.72, "USD"),
                                obs("2020-10-19", 32.01, "USD"),
                                obs("2020-10-18", 32.12, "USD"),
                                obs("2020-10-17", 32.12, "USD"),
                                obs("2020-10-16", 32.12, "USD"),
                                obs("2020-10-15", 31.46, "USD"),
                                obs("2020-10-14", 31.93, "USD"),
                                obs("2020-10-13", 31.87, "USD"),
                                obs("2020-10-12", 31.13, "USD"),
                                obs("2020-10-11", 30.03, "USD"),
                                obs("2020-10-10", 30.03, "USD")
                        )))
                .build();

        BillingInstruction expectedBillingInstruction = assertJsonConformsToRosettaType("/cdm-sample-files/functions/sec-lending/create-security-lending-invoice-func-input.json", BillingInstruction.class);

        assertEquals(STRICT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(expectedBillingInstruction),
                STRICT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(actualBillingInstruction),
                "The input JSON for create-security-lending-invoice-func-input.json has been updated (probably due to a model change). Update the input file");
    }

    private BillingRecordInstruction createBillingRecordInstruction(TradeState transferTradeState, Date billingStartDate, Date billingEndDate, Date settlementDate, List<Observation> observations) {
        return BillingRecordInstruction.builder()
                .setTradeStateValue(transferTradeState)
                .setRecordStartDate(billingStartDate)
                .setRecordEndDate(billingEndDate)
                .setSettlementDate(settlementDate)
                .setObservation(observations)
                .build();
    }

    private Observation obs(String date, double price, @SuppressWarnings("SameParameterValue") String currency) {
        return Observation.builder()
                .setObservationIdentifier(ObservationIdentifier.builder()
                        .setObservationDate(Date.of(LocalDate.parse(date)))
                        .build())
                .setObservedValue(Price.builder()
                        .setAmount(BigDecimal.valueOf(price))
                        .setUnitOfAmount(UnitType.builder().setCurrencyValue(currency).build())
                        .build())
                .build();
    }


    private static TradeState getBlockExecutionTradeState() throws IOException {
        URL resource = SecLendingFunctionInputCreationTest.class.getResource(EXECUTION_INSTRUCTION_JSON);
        ExecutionInstruction executionInstruction = STRICT_MAPPER.readValue(resource, ExecutionInstruction.class);
        Create_Execution createExecution = injector.getInstance(Create_Execution.class);

        BusinessEvent businessEvent = createExecution.evaluate(executionInstruction);

        assertNotNull(businessEvent, "Expected a business event");
        List<? extends PrimitiveEvent> primitives = businessEvent.getPrimitives();
        assertNotNull(primitives, "Expected a primitive");
        assertEquals(1, primitives.size(), "Expected 1 primitive but was " + primitives.size());
        PrimitiveEvent primitiveEvent = primitives.get(0);
        ExecutionPrimitive executionPrimitive = primitiveEvent.getExecution();
        assertNotNull(executionPrimitive, "Expected an execution");
        TradeState afterTradeState = executionPrimitive.getAfter();
        assertNotNull(afterTradeState, "Expected an after trade state");
        return afterTradeState;
    }


    private static TradeState getTransferTradeState() throws IOException {
        URL resource = SecLendingFunctionInputCreationTest.class.getResource(SETTLEMENT_WORKFLOW_FUNC_INPUT_JSON);
        ExecutionInstruction executionInstruction = STRICT_MAPPER.readValue(resource, ExecutionInstruction.class);
        RunNewSettlementWorkflow runNewSettlementWorkflow = injector.getInstance(RunNewSettlementWorkflow.class);
        Workflow.WorkflowBuilder workflowBuilder = runNewSettlementWorkflow.execute(executionInstruction).toBuilder();
        ResourcesUtils.reKey(workflowBuilder);
        Workflow workflow = workflowBuilder.build();

        assertNotNull(workflow, "Expected a workflow");
        List<? extends WorkflowStep> workflowSteps = workflow.getSteps();
        assertNotNull(workflowSteps, "Expected workflow " + workflow + " to have some steps");
        assertEquals(3, workflowSteps.size(), "Expected 3 workflow steps but was " + workflowSteps.size());
        WorkflowStep workflowStep = workflowSteps.get(2);
        BusinessEvent businessEvent = workflowStep.getBusinessEvent();
        assertNotNull(businessEvent, "Expected a business event");
        List<? extends PrimitiveEvent> primitives = businessEvent.getPrimitives();
        assertNotNull(primitives, "Expected a primitive");
        assertEquals(1, primitives.size(), "Expected 1 primitive but was " + primitives.size());
        PrimitiveEvent primitiveEvent = primitives.get(0);
        TransferPrimitive primitiveEventTransfer = primitiveEvent.getTransfer();
        assertNotNull(primitiveEventTransfer, "Expected a transfer");
        TradeState afterTradeState = primitiveEventTransfer.getAfter();
        assertNotNull(afterTradeState, "Expected an after trade state");
        return afterTradeState;
    }

    private PrimitiveInstruction createAllocationInstruction(TradeState tradeState, String externalKey, String partyId, double percent) {
        Party agentLenderParty = getParty(tradeState, CounterpartyRoleEnum.PARTY_1);
        Identifier allocationIdentifier = createAllocationIdentifier(tradeState.build().toBuilder(), "allocation-" + externalKey);
        List<Quantity> allocatedQuantities = scaleQuantities(tradeState, percent);

        PartyChangeInstruction.PartyChangeInstructionBuilder partyChangeInstruction = PartyChangeInstruction.builder()
                .setCounterparty(Counterparty.builder()
                        .setPartyReferenceValue(Party.builder()
                                .setMeta(MetaFields.builder().setExternalKey(externalKey))
                                .setNameValue(partyId)
                                .addPartyIdValue(partyId))
                        .setRole(CounterpartyRoleEnum.PARTY_1))
                .setPartyRole(PartyRole.builder()
                        .setPartyReferenceValue(agentLenderParty)
                        .setRole(PartyRoleEnum.AGENT_LENDER))
                .setTradeId(Lists.newArrayList(allocationIdentifier));

        QuantityChangeInstruction.QuantityChangeInstructionBuilder quantityChangeInstruction = QuantityChangeInstruction.builder()
                .setDirection(QuantityChangeDirectionEnum.REPLACE)
                .addChange(PriceQuantity.builder()
                        .setQuantityValue(allocatedQuantities));

        return PrimitiveInstruction.builder()
                .setPartyChange(partyChangeInstruction)
                .setQuantityChange(quantityChangeInstruction);
    }

    private static AllocationBreakdown.AllocationBreakdownBuilder createAllocationBreakdown(TradeState tradeState, String partyId, String partyName, double percent) {
        Identifier allocationIdentifier = createAllocationIdentifier(tradeState.build()
                .toBuilder(), "allocation-" + partyId);

        Party agentLenderParty = getParty(tradeState, CounterpartyRoleEnum.PARTY_1);

        PartyRole agentLenderPartyRole = PartyRole.builder()
                .setPartyReferenceValue(agentLenderParty)
                .setRole(PartyRoleEnum.AGENT_LENDER).build();

        Counterparty counterparty1 = Counterparty.builder()
                .setPartyReferenceValue(Party.builder()
                        .setMeta(MetaFields.builder().setExternalKey(partyId))
                        .addPartyIdValue(partyName)
                        .setNameValue(partyName))
                .setRole(CounterpartyRoleEnum.PARTY_1)
                .build();

        List<Quantity> allocatedQuantities = scaleQuantities(tradeState, percent);

        AllocationBreakdown.AllocationBreakdownBuilder allocationBreakdownBuilder = AllocationBreakdown.builder()
                .addAllocationTradeId(allocationIdentifier)
                .setCounterparty(counterparty1)
                .setQuantity(allocatedQuantities)
                .setAncillaryParty(agentLenderPartyRole);

        ResourcesUtils.reKey(allocationBreakdownBuilder);

        return allocationBreakdownBuilder;
    }

    private static Party getParty(TradeState tradeState, CounterpartyRoleEnum counterpartyRoleEnum) {
        return tradeState.build().toBuilder().getTrade().getTradableProduct()
                .getCounterparty().stream()
                .filter(c -> c.getRole() == counterpartyRoleEnum)
                .map(Counterparty::getPartyReference)
                .map(ReferenceWithMetaParty::getValue)
                .findFirst().orElseThrow(RuntimeException::new);
    }

    @NotNull
    private static List<Quantity> scaleQuantities(TradeState tradeState, double percent) {
        return tradeState.build().toBuilder().getTrade().getTradableProduct()
                .getTradeLot().stream()
                .map(TradeLot.TradeLotBuilder::getPriceQuantity)
                .flatMap(Collection::stream)
                .map(PriceQuantity::getQuantity)
                .flatMap(Collection::stream)
                .map(FieldWithMetaQuantity::getValue)
                .map(Quantity::toBuilder)
                .map(q -> q.setAmount(q.getAmount().multiply(BigDecimal.valueOf(percent))))
                .map(Quantity::build)
                .collect(Collectors.toList());
    }

    private static Identifier createAllocationIdentifier(TradeState tradeState, String allocationName) {
        Identifier.IdentifierBuilder allocationIdentifierBuilder = tradeState.getTrade().getTradeIdentifier().get(0)
                .build().toBuilder();
        allocationIdentifierBuilder.getAssignedIdentifier()
                .forEach(c -> c.setIdentifierValue(c.getIdentifier().getValue() + "-" + allocationName));
        return allocationIdentifierBuilder.build();
    }

    private static ObjectNode asJsonNode(Map<String, Object> objectMap) {
        ObjectNode actual = STRICT_MAPPER.createObjectNode();
        objectMap.forEach((k, v) -> actual.setAll(STRICT_MAPPER.createObjectNode().putPOJO(k, v)));
        return actual;
    }

    private static String readResource(String inputJson) throws IOException {
        //noinspection UnstableApiUsage
        return Resources
                .toString(Objects
                        .requireNonNull(SecLendingFunctionInputCreationTest.class.getResource(inputJson)), Charset
                        .defaultCharset());
    }

    private <T> T assertJsonConformsToRosettaType(JsonNode node, Class<T> rosettaType) throws IOException {
        T actual = MAPPER.convertValue(node, rosettaType);

        assertEquals(STRICT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(node),
                STRICT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(actual),
                "The input JSON has been updated (probably due to a model change). Update the input file");
        return actual;
    }

    private <T> T assertJsonConformsToRosettaType(String inputJson, Class<T> rosettaType) throws IOException {
        URL expectedURL = SecLendingFunctionInputCreationTest.class.getResource(inputJson);
        // dont use the strict one here as we want to see the diff to help us fix
        T actual = MAPPER.readValue(expectedURL, rosettaType);

        assertEquals(readResource(inputJson),
                STRICT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(actual),
                "The input JSON for " + inputJson + " has been updated (probably due to a model change). Update the input file");
        return actual;
    }

    private void assertJsonEquals(String expectedJsonPath, Object actual) throws IOException {
        String actualJson = STRICT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(actual);
        String expectedJson = ResourcesUtils.getJson(expectedJsonPath);
        if (!expectedJson.equals(actualJson)) {
            if (WRITE_EXPECTATIONS) {
                writeExpectation(expectedJsonPath, actualJson);
            }
        }
        assertEquals(expectedJson, actualJson,
                "The input JSON for " + Paths.get(expectedJsonPath).getFileName() + " has been updated (probably due to a model change). Update the input file");
    }

    private void writeExpectation(String writePath, String json) {
        // Add environment variable TEST_WRITE_BASE_PATH to override the base write path, e.g.
        // TEST_WRITE_BASE_PATH=/Users/hugohills/dev/github/REGnosys/rosetta-cdm/rosetta-source/src/main/resources/
        TEST_WRITE_BASE_PATH.filter(Files::exists).ifPresent(basePath -> {
            Path expectationFilePath = basePath.resolve(writePath);
            try {
                Files.createDirectories(expectationFilePath.getParent());
                Files.write(expectationFilePath, json.getBytes());
                LOGGER.warn("Updated expectation file {}", expectationFilePath.toAbsolutePath());
            } catch (IOException e) {
                LOGGER.error("Failed to write expectation file {}", expectationFilePath.toAbsolutePath(), e);
            }
        });
    }
}