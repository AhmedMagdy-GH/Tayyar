package com.tayyar.order;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.tayyar.order.OrderDtos.*;
import com.tayyar.payment.*;
import com.tayyar.support.PostgresIntegrationTest;
import java.math.BigDecimal;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@SpringBootTest
class OrderPaymentFoundationIT extends PostgresIntegrationTest {
  @Autowired JdbcTemplate jdbc;
  @Autowired PlatformTransactionManager transactions;
  @Autowired OrderService orders;
  @Autowired PaymentService payments;
  @Autowired RequestMappingHandlerMapping mappings;
  @MockitoSpyBean OrderStore orderStore;
  @MockitoSpyBean PaymentStore paymentStore;

  UUID customer, restaurant, branch, menu, category, item, savedAddress;

  @BeforeEach
  void fixture() {
    customer = UUID.randomUUID();
    restaurant = UUID.randomUUID();
    branch = UUID.randomUUID();
    menu = UUID.randomUUID();
    category = UUID.randomUUID();
    item = UUID.randomUUID();
    savedAddress = UUID.randomUUID();
    new TransactionTemplate(transactions)
        .executeWithoutResult(
            status -> {
              jdbc.update(
                  "INSERT INTO"
                      + " users(id,full_name,email,password_hash,status,created_at,updated_at)"
                      + " VALUES (?,'Order"
                      + " Customer',?,'unused','ACTIVE',now(),now())",
                  customer,
                  customer + "@example.com");
              jdbc.update("INSERT INTO user_roles VALUES (?,'CUSTOMER')", customer);
              UUID application = UUID.randomUUID();
              jdbc.update(
                  "INSERT INTO"
                      + " restaurant_applications(id,applicant_id,status,current_revision,created_at,updated_at)"
                      + " VALUES (?,?,'APPROVED',1,now(),now())",
                  application,
                  customer);
              jdbc.update(
                  "INSERT INTO application_submissions VALUES (?,1,'Snapshot"
                      + " Kitchen','Original',now())",
                  application);
              jdbc.update(
                  "INSERT INTO"
                      + " restaurants(id,application_id,name,description,status,created_at,updated_at)"
                      + " VALUES (?,?,'Snapshot Kitchen','Original','ACTIVE',now(),now())",
                  restaurant,
                  application);
              jdbc.update(
                  "INSERT INTO restaurant_memberships VALUES (?,?,'OWNER',now())",
                  restaurant,
                  customer);
              insertBranch(branch, restaurant, "Snapshot Branch");
              jdbc.update(
                  "INSERT INTO"
                      + " restaurant_menus(id,restaurant_id,name,created_at,updated_at)"
                      + " VALUES (?,?,'Main',now(),now())",
                  menu,
                  restaurant);
              jdbc.update(
                  "INSERT INTO"
                      + " menu_categories(id,menu_id,restaurant_id,name,position,created_at,updated_at)"
                      + " VALUES (?,?,?,'Meals',0,now(),now())",
                  category,
                  menu,
                  restaurant);
              jdbc.update(
                  "INSERT INTO"
                      + " menu_items(id,category_id,restaurant_id,name,base_price,position,created_at,updated_at)"
                      + " VALUES (?,?,?,'Original Chicken',80,0,now(),now())",
                  item,
                  category,
                  restaurant);
              jdbc.update(
                  "INSERT INTO"
                      + " customer_addresses(id,user_id,label,street,building,city,country_code,created_at,updated_at)"
                      + " VALUES (?,?,'Old Home','Old Street','12','Cairo','EG',now(),now())",
                  savedAddress,
                  customer);
            });
  }

  void insertBranch(UUID id, UUID restaurantId, String name) {
    jdbc.update(
        """
INSERT INTO branches(id,restaurant_id,name,address_line1,city,country_code,timezone,delivery_model,status,created_at,updated_at)
VALUES (?,?,?,'Street','Cairo','EG','Africa/Cairo','RESTAURANT_DELIVERY','ACTIVE',now(),now())
""",
        id,
        restaurantId,
        name);
  }

  AddressSnapshot address() {
    return new AddressSnapshot(
        "Old Home",
        "Old Street",
        "12",
        "3",
        "8",
        "Old landmark",
        "Ring bell",
        "Cairo",
        "Cairo",
        "11511",
        "EG",
        new BigDecimal("30.044400"),
        new BigDecimal("31.235700"),
        UUID.randomUUID(),
        "Old Zone",
        "Cairo");
  }

  Draft draft(OrderStatus initial) {
    return new Draft(
        customer,
        restaurant,
        branch,
        initial,
        List.of(new PurchaseItem(item, "Original Chicken", new BigDecimal("80.00"), 2)),
        address(),
        new BigDecimal("25.00"),
        new BigDecimal("5.00"));
  }

  Details create(OrderStatus initial) {
    return orders.create(draft(initial), TransitionActor.system());
  }

  @Test
  void validOrderPersistsCalculatedMoneyAndInitialHistory() {
    Details details = create(OrderStatus.PLACED);
    assertThat(details.order().customerId()).isEqualTo(customer);
    assertThat(details.order().restaurantId()).isEqualTo(restaurant);
    assertThat(details.order().branchId()).isEqualTo(branch);
    assertThat(details.order().money().currency()).isEqualTo("EGP");
    assertThat(details.order().money().merchandiseSubtotal()).isEqualByComparingTo("160");
    assertThat(details.order().money().deliveryFee()).isEqualByComparingTo("25");
    assertThat(details.order().money().discountTotal()).isEqualByComparingTo("5");
    assertThat(details.order().money().finalTotal()).isEqualByComparingTo("180");
    assertThat(details.items())
        .singleElement()
        .satisfies(
            line -> {
              assertThat(line.purchasedName()).isEqualTo("Original Chicken");
              assertThat(line.unitPrice()).isEqualByComparingTo("80");
              assertThat(line.quantity()).isEqualTo(2);
              assertThat(line.lineSubtotal()).isEqualByComparingTo("160");
            });
    assertThat(orders.history(details.order().id()))
        .singleElement()
        .satisfies(
            event -> {
              assertThat(event.previousStatus()).isNull();
              assertThat(event.newStatus()).isEqualTo(OrderStatus.PLACED);
              assertThat(event.actorKind()).isEqualTo(TransitionActor.Kind.SYSTEM);
            });
  }

  @Test
  void itemAndAddressSnapshotsSurviveMutableSourceChangesAndAddressDeletion() {
    Details original = create(OrderStatus.PLACED);
    jdbc.update("UPDATE menu_items SET name='Renamed',base_price=999 WHERE id=?", item);
    jdbc.update(
        "UPDATE customer_addresses SET street='New Street',building='99' WHERE id=?", savedAddress);
    jdbc.update("DELETE FROM customer_addresses WHERE id=?", savedAddress);
    Details retained = orders.details(original.order().id());
    assertThat(retained.items().getFirst().purchasedName()).isEqualTo("Original Chicken");
    assertThat(retained.items().getFirst().unitPrice()).isEqualByComparingTo("80");
    assertThat(retained.address().street()).isEqualTo("Old Street");
    assertThat(retained.address().building()).isEqualTo("12");
    assertThat(retained.address().deliveryZoneName()).isEqualTo("Old Zone");
  }

  @Test
  void branchRestaurantContextAndMoneyAreValidatedInApplicationAndDatabase() {
    UUID otherRestaurant = restaurant("Other Context");
    UUID otherBranch = UUID.randomUUID();
    insertBranch(otherBranch, otherRestaurant, "Other Branch");
    Draft mismatch =
        new Draft(
            customer,
            restaurant,
            otherBranch,
            OrderStatus.PLACED,
            draft(OrderStatus.PLACED).items(),
            address(),
            BigDecimal.ZERO,
            BigDecimal.ZERO);
    assertThatThrownBy(() -> orders.create(mismatch, TransitionActor.system()))
        .isInstanceOf(OrderException.class);
    for (BigDecimal invalid :
        List.of(new BigDecimal("-1"), new BigDecimal("1.001"), new BigDecimal("10000000000"))) {
      Draft bad =
          new Draft(
              customer,
              restaurant,
              branch,
              OrderStatus.PLACED,
              List.of(new PurchaseItem(item, "Item", invalid, 1)),
              address(),
              BigDecimal.ZERO,
              BigDecimal.ZERO);
      assertThatThrownBy(() -> orders.create(bad, TransitionActor.system()))
          .isInstanceOf(OrderException.class);
    }
    sqlState(
        "23514",
        () ->
            jdbc.update(
                "INSERT INTO orders(id,customer_id,restaurant_id,branch_id,status,currency,"
                    + "merchandise_subtotal,delivery_fee,discount_total,final_total,created_at,updated_at)"
                    + " VALUES (?,?,?,?,'PLACED','EGP',10,0,0,11,now(),now())",
                UUID.randomUUID(),
                customer,
                restaurant,
                branch));
  }

  @Test
  void orderLifecycleHistoryAndTerminalRulesHold() {
    View order = create(OrderStatus.PLACED).order();
    for (OrderStatus status :
        List.of(
            OrderStatus.ACCEPTED,
            OrderStatus.PREPARING,
            OrderStatus.READY_FOR_PICKUP,
            OrderStatus.OUT_FOR_DELIVERY,
            OrderStatus.DELIVERED))
      order =
          orders.transition(order.id(), order.version(), status, TransitionActor.system(), null);
    assertThat(order.status()).isEqualTo(OrderStatus.DELIVERED);
    assertThat(orders.history(order.id())).hasSize(6);
    long version = order.version();
    UUID deliveredId = order.id();
    assertThatThrownBy(
            () ->
                orders.transition(
                    deliveredId, version, OrderStatus.PREPARING, TransitionActor.system(), null))
        .isInstanceOf(OrderException.class);
    assertThat(orders.details(deliveredId).order().status()).isEqualTo(OrderStatus.DELIVERED);
  }

  @Test
  void sideStatesAndCancellationFoundationAreExplicit() {
    View pending = create(OrderStatus.PENDING_PAYMENT).order();
    assertThatThrownBy(
            () ->
                orders.transition(
                    pending.id(),
                    pending.version(),
                    OrderStatus.PAYMENT_FAILED,
                    TransitionActor.system(),
                    null))
        .isInstanceOf(OrderException.class);
    View failed =
        orders.transition(
            pending.id(),
            pending.version(),
            OrderStatus.PAYMENT_FAILED,
            TransitionActor.system(),
            "authorization failed");
    assertThatThrownBy(
            () ->
                orders.transition(
                    failed.id(),
                    failed.version(),
                    OrderStatus.DELIVERED,
                    TransitionActor.system(),
                    null))
        .isInstanceOf(OrderException.class);
    View placed = create(OrderStatus.PLACED).order();
    View cancelled =
        orders.transition(
            placed.id(),
            placed.version(),
            OrderStatus.CANCELLED,
            TransitionActor.system(),
            "customer cancelled before acceptance");
    assertThat(cancelled.status()).isEqualTo(OrderStatus.CANCELLED);
    View accepted = create(OrderStatus.PLACED).order();
    accepted =
        orders.transition(
            accepted.id(),
            accepted.version(),
            OrderStatus.ACCEPTED,
            TransitionActor.system(),
            null);
    long acceptedVersion = accepted.version();
    UUID acceptedId = accepted.id();
    assertThatThrownBy(
            () ->
                orders.transition(
                    acceptedId,
                    acceptedVersion,
                    OrderStatus.CANCELLED,
                    TransitionActor.system(),
                    "too late"))
        .isInstanceOf(OrderException.class);
  }

  @Test
  void orderTransitionAndHistoryAreAtomicOnFailure() {
    View order = create(OrderStatus.PLACED).order();
    doThrow(new IllegalStateException("injected history failure"))
        .when(orderStore)
        .history(
            eq(order.id()),
            eq(OrderStatus.PLACED),
            eq(OrderStatus.ACCEPTED),
            any(TransitionActor.class),
            isNull(),
            any());
    assertThatThrownBy(
            () ->
                orders.transition(
                    order.id(),
                    order.version(),
                    OrderStatus.ACCEPTED,
                    TransitionActor.system(),
                    null))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("injected history failure");
    reset(orderStore);
    assertThat(orders.details(order.id()).order().status()).isEqualTo(OrderStatus.PLACED);
    assertThat(orders.details(order.id()).order().version()).isZero();
    assertThat(orders.history(order.id())).hasSize(1);
  }

  @Test
  void concurrentOrderTransitionsHaveOneWinnerAndOneConflict() throws Exception {
    View order = create(OrderStatus.PLACED).order();
    var pool = Executors.newFixedThreadPool(2);
    var ready = new CountDownLatch(2);
    var start = new CountDownLatch(1);
    Callable<String> accept = transition(order, OrderStatus.ACCEPTED, null, ready, start);
    Callable<String> cancel = transition(order, OrderStatus.CANCELLED, "cancelled", ready, start);
    Future<String> first = pool.submit(accept), second = pool.submit(cancel);
    assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
    start.countDown();
    assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
        .containsExactlyInAnyOrder("SUCCESS", "CONFLICT");
    pool.shutdownNow();
    assertThat(orders.history(order.id())).hasSize(2);
    assertThat(orders.details(order.id()).order().version()).isEqualTo(1);
  }

  Callable<String> transition(
      View order, OrderStatus target, String reason, CountDownLatch ready, CountDownLatch start) {
    return () -> {
      ready.countDown();
      start.await();
      try {
        orders.transition(order.id(), order.version(), target, TransitionActor.system(), reason);
        return "SUCCESS";
      } catch (OrderException exception) {
        assertThat(exception.status().value()).isEqualTo(409);
        return "CONFLICT";
      }
    };
  }

  @Test
  void cashPaymentIsSeparateAndUsesOrderAmount() {
    View order = create(OrderStatus.PLACED).order();
    PaymentDtos.View payment =
        payments.create(
            new PaymentDtos.Create(order.id(), PaymentMethod.CASH, null, null),
            TransitionActor.system());
    assertThat(payment.orderId()).isEqualTo(order.id());
    assertThat(payment.amount()).isEqualByComparingTo(order.money().finalTotal());
    assertThat(payment.currency()).isEqualTo("EGP");
    assertThat(payment.status()).isEqualTo(PaymentStatus.PENDING);
    payment =
        payments.transition(
            payment.id(), payment.version(), PaymentStatus.PAID, TransitionActor.system(), null);
    assertThat(payment.status()).isEqualTo(PaymentStatus.PAID);
    assertThat(orders.details(order.id()).order().status()).isEqualTo(OrderStatus.PLACED);
    assertThat(payments.history(payment.id())).hasSize(2);
  }

  @Test
  void cardRulesExistWithoutFakeProviderProcessing() {
    View order = create(OrderStatus.PENDING_PAYMENT).order();
    PaymentDtos.View card =
        payments.create(
            new PaymentDtos.Create(order.id(), PaymentMethod.CARD, null, null),
            TransitionActor.system());
    assertThat(card.status()).isEqualTo(PaymentStatus.PENDING);
    assertThat(card.provider()).isNull();
    assertThat(card.providerReference()).isNull();
    UUID cardId = card.id();
    long cardVersion = card.version();
    assertThatThrownBy(
            () ->
                payments.transition(
                    cardId, cardVersion, PaymentStatus.PAID, TransitionActor.provider(), null))
        .isInstanceOf(PaymentException.class);
    card =
        payments.transition(
            card.id(), card.version(), PaymentStatus.AUTHORIZED, TransitionActor.provider(), null);
    card =
        payments.transition(
            card.id(), card.version(), PaymentStatus.PAID, TransitionActor.provider(), null);
    assertThat(card.status()).isEqualTo(PaymentStatus.PAID);
    assertThat(orders.details(order.id()).order().status()).isEqualTo(OrderStatus.PENDING_PAYMENT);
  }

  @Test
  void paymentValidationHistoryRollbackAndStaleVersionHold() {
    View order = create(OrderStatus.PLACED).order();
    assertThatThrownBy(
            () ->
                payments.create(
                    new PaymentDtos.Create(
                        order.id(), PaymentMethod.CASH, "fake-provider", "reference"),
                    TransitionActor.system()))
        .isInstanceOf(PaymentException.class);
    PaymentDtos.View cash =
        payments.create(
            new PaymentDtos.Create(order.id(), PaymentMethod.CASH, null, null),
            TransitionActor.system());
    doThrow(new IllegalStateException("injected payment history failure"))
        .when(paymentStore)
        .history(
            eq(cash.id()),
            eq(PaymentStatus.PENDING),
            eq(PaymentStatus.PAID),
            any(TransitionActor.class),
            isNull(),
            any());
    assertThatThrownBy(
            () ->
                payments.transition(
                    cash.id(), cash.version(), PaymentStatus.PAID, TransitionActor.system(), null))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("injected payment history failure");
    reset(paymentStore);
    assertThat(payments.details(cash.id()).status()).isEqualTo(PaymentStatus.PENDING);
    PaymentDtos.View paid =
        payments.transition(
            cash.id(), cash.version(), PaymentStatus.PAID, TransitionActor.system(), null);
    assertThatThrownBy(
            () ->
                payments.transition(
                    paid.id(), 0, PaymentStatus.REFUNDED, TransitionActor.system(), "refund"))
        .isInstanceOf(PaymentException.class)
        .extracting(exception -> ((PaymentException) exception).status().value())
        .isEqualTo(409);
  }

  @Test
  void databaseProtectsPaymentAmountStateContextAndProviderReferences() {
    View order = create(OrderStatus.PLACED).order();
    PaymentDtos.View cash =
        payments.create(
            new PaymentDtos.Create(order.id(), PaymentMethod.CASH, null, null),
            TransitionActor.system());
    sqlState(
        "23503",
        () ->
            jdbc.update(
                "INSERT INTO"
                    + " payments(id,order_id,method,status,amount,currency,created_at,updated_at)"
                    + " VALUES (?,?,'CASH','PENDING',181,'EGP',now(),now())",
                UUID.randomUUID(),
                order.id()));
    sqlState(
        "23514",
        () -> jdbc.update("UPDATE payments SET status='AUTHORIZED' WHERE id=?", cash.id()));
    sqlState("23514", () -> jdbc.update("UPDATE payments SET amount=1 WHERE id=?", cash.id()));
    PaymentDtos.View card =
        payments.create(
            new PaymentDtos.Create(order.id(), PaymentMethod.CARD, "provider", "unique-reference"),
            TransitionActor.system());
    assertThat(card.providerReference()).isEqualTo("unique-reference");
    assertThatThrownBy(
            () ->
                payments.create(
                    new PaymentDtos.Create(
                        order.id(), PaymentMethod.CARD, "provider", "unique-reference"),
                    TransitionActor.system()))
        .isInstanceOf(PaymentException.class);
  }

  @Test
  void historicalRowsRejectMutationDeletionAndDestructiveCascades() {
    Details details = create(OrderStatus.PLACED);
    UUID order = details.order().id();
    UUID orderItem = details.items().getFirst().id();
    UUID history = orders.history(order).getFirst().id();
    List<org.assertj.core.api.ThrowableAssert.ThrowingCallable> protectedMutations =
        List.of(
            () -> jdbc.update("DELETE FROM orders WHERE id=?", order),
            () ->
                jdbc.update(
                    "UPDATE order_items SET purchased_name='Changed' WHERE" + " id=?", orderItem),
            () -> jdbc.update("DELETE FROM order_address_snapshots WHERE order_id=?", order),
            () -> jdbc.update("DELETE FROM order_status_history WHERE id=?", history),
            () ->
                jdbc.update("UPDATE orders SET branch_id=? WHERE id=?", UUID.randomUUID(), order));
    for (var work : protectedMutations) sqlState("23514", work);
    sqlState("23503", () -> jdbc.update("DELETE FROM menu_items WHERE id=?", item));
  }

  @Test
  void databaseContextConstraintsIndexesAndNoCardSecretsExist() {
    Details details = create(OrderStatus.PLACED);
    UUID otherRestaurant = restaurant("Other Item Context");
    UUID otherBranch = UUID.randomUUID();
    insertBranch(otherBranch, otherRestaurant, "Other");
    UUID otherMenu = UUID.randomUUID(),
        otherCategory = UUID.randomUUID(),
        otherItem = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO restaurant_menus(id,restaurant_id,name,created_at,updated_at) VALUES"
            + " (?,?,'Other',now(),now())",
        otherMenu,
        otherRestaurant);
    jdbc.update(
        "INSERT INTO"
            + " menu_categories(id,menu_id,restaurant_id,name,position,created_at,updated_at)"
            + " VALUES (?,?,?,'Other',0,now(),now())",
        otherCategory,
        otherMenu,
        otherRestaurant);
    jdbc.update(
        "INSERT INTO"
            + " menu_items(id,category_id,restaurant_id,name,base_price,position,created_at,updated_at)"
            + " VALUES (?,?,?,'Other',10,0,now(),now())",
        otherItem,
        otherCategory,
        otherRestaurant);
    sqlState(
        "23503",
        () ->
            jdbc.update(
                "INSERT INTO"
                    + " order_items(id,order_id,restaurant_id,menu_item_id,purchased_name,unit_price,quantity,line_subtotal,created_at)"
                    + " VALUES (?,?,?,?, 'Other',10,1,10,now())",
                UUID.randomUUID(),
                details.order().id(),
                otherRestaurant,
                otherItem));
    assertThat(
            jdbc.queryForList(
                "SELECT indexname FROM pg_indexes WHERE tablename IN" + " ('orders','payments')",
                String.class))
        .contains(
            "orders_customer_history_idx",
            "orders_branch_queue_idx",
            "orders_restaurant_queue_idx",
            "payments_order_idx",
            "payments_provider_reference_uq");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.columns WHERE"
                    + " table_name='payments' AND (column_name ILIKE"
                    + " '%card_number%' OR column_name ILIKE '%cvv%' OR column_name"
                    + " ILIKE '%card_token%')",
                Integer.class))
        .isZero();
    assertThat(
            mappings.getHandlerMethods().values().stream()
                .map(mapping -> mapping.getBeanType().getPackageName())
                .noneMatch(
                    name -> name.equals("com.tayyar.order") || name.equals("com.tayyar.payment")))
        .isTrue();
  }

  UUID restaurant(String name) {
    UUID owner = UUID.randomUUID(), application = UUID.randomUUID(), id = UUID.randomUUID();
    new TransactionTemplate(transactions)
        .executeWithoutResult(
            status -> {
              jdbc.update(
                  "INSERT INTO"
                      + " users(id,full_name,email,password_hash,status,created_at,updated_at)"
                      + " VALUES (?,'Owner',?,'unused','ACTIVE',now(),now())",
                  owner,
                  owner + "@example.com");
              jdbc.update(
                  "INSERT INTO"
                      + " restaurant_applications(id,applicant_id,status,current_revision,created_at,updated_at)"
                      + " VALUES (?,?,'APPROVED',1,now(),now())",
                  application,
                  owner);
              jdbc.update(
                  "INSERT INTO application_submissions VALUES" + " (?,1,?,'Test',now())",
                  application,
                  name);
              jdbc.update(
                  "INSERT INTO"
                      + " restaurants(id,application_id,name,description,status,created_at,updated_at)"
                      + " VALUES (?,?,?,'Test','ACTIVE',now(),now())",
                  id,
                  application,
                  name);
              jdbc.update(
                  "INSERT INTO restaurant_memberships VALUES (?,?,'OWNER',now())", id, owner);
            });
    return id;
  }

  void sqlState(String expected, org.assertj.core.api.ThrowableAssert.ThrowingCallable work) {
    assertThatThrownBy(work)
        .rootCause()
        .isInstanceOfSatisfying(
            SQLException.class, error -> assertThat(error.getSQLState()).isEqualTo(expected));
  }
}
