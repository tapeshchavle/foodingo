package com.food.service.impl;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.food.entity.OrderEntity;
import com.food.io.OrderRequest;
import com.food.io.OrderResponse;
import com.food.repository.CartRepository;
import com.food.repository.OrderRepository;
import com.food.service.OrderService;
import com.food.service.UserService;
import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;

@Service
public class OrderServiceImpl implements OrderService {
	@Autowired
	private OrderRepository orderRepository;
	@Autowired
	private CartRepository cartRepository;

	@Value("${razorpay.key}")
	private String RAZORPAY_KEY;
	@Value("${razorpay.secret}")
	private String RAZORPAY_SECRET;
	@Autowired
	private UserService userService;

	@Override
	public OrderResponse createOrderPayment(OrderRequest request) throws RazorpayException {
		OrderEntity orderEntity = convertToOrderEntity(request);
		orderEntity = orderRepository.save(orderEntity);
		// rozar pay
		RazorpayClient razorpayClient = new RazorpayClient(RAZORPAY_KEY, RAZORPAY_SECRET);
		JSONObject orderRequest = new JSONObject();
		orderRequest.put("amount", orderEntity.getAmount());
		orderRequest.put("currency", "INR");
		orderRequest.put("payment_capture", 1);
		Order razorpayOrder = razorpayClient.orders.create(orderRequest);
		orderEntity.setRazorpayOrderId(razorpayOrder.get("id"));
		String loggedInUserId = userService.findByUserId();
		orderEntity.setUserId(loggedInUserId);
		orderEntity.setOrderStatus(request.getOrderStatus());
		orderEntity = orderRepository.save(orderEntity);
		return convertToOrderResponse(orderEntity);
	}

	private OrderEntity convertToOrderEntity(OrderRequest request) {
		return OrderEntity.builder()
				.userAddress(request.getUserAddress())
				.amount(request.getAmount())
				.orderedItems(request.getOrderedItems())
				.email(request.getEmail())
				.phoneNumber(request.getPhoneNumber())
				.paymentStatus(request.getOrderStatus())
				.build();
	}

	private OrderResponse convertToOrderResponse(OrderEntity orderEntity) {
		return OrderResponse.builder()
				.id(orderEntity.getId())
				.userId(orderEntity.getUserId())
				.userAddress(orderEntity.getUserAddress())
				.phoneNumber(orderEntity.getPhoneNumber())
				.email(orderEntity.getEmail())
				.orderedItems(orderEntity.getOrderedItems())
				.amount(orderEntity.getAmount())
				.paymentStatus(orderEntity.getPaymentStatus())
				.orderStatus(orderEntity.getOrderStatus())
				.razorpayOrderId(orderEntity.getRazorpayOrderId())
				.build();
	}

	@Override
	public void verifyPayment(Map<String, String> paymentData, String status) {
		String razorpayOrderId = paymentData.get("razorpay_order_id");
		OrderEntity existingOrder = orderRepository.findByRazorpayOrderId(razorpayOrderId)
				.orElseThrow(() -> new RuntimeException("Order not found"));

		existingOrder.setPaymentStatus(status);
		existingOrder.setRazorpaySignature(paymentData.get("razorpay_signature"));
		existingOrder.setRazorpayPaymentId(paymentData.get("razorpay_payment_id"));
		orderRepository.save(existingOrder);

		if ("paid".equalsIgnoreCase(status)) {
			cartRepository.deleteByUserId(existingOrder.getUserId());

		}

	}

	@Override
	public List<OrderResponse> getUserOrders() {
		String loggedInUserId = userService.findByUserId();
		List<OrderEntity> orderEntities = orderRepository.findByUserId(loggedInUserId);
		List<OrderResponse> orderResponses = orderEntities.stream().map(e -> convertToOrderResponse(e))
				.collect(Collectors.toList());
		return orderResponses;
	}

	@Override
	public void removeOrder(String orderId) {
		orderRepository.deleteById(orderId);
	}

	@Override
	public List<OrderResponse> getAllUserOrdes() {
		List<OrderEntity> orderEntities = orderRepository.findAll();
		return orderEntities.stream().map(entity -> convertToOrderResponse(entity)).collect(Collectors.toList());
	}

	@Override
	public void updateOrderStatus(String orderId, String status) {
		OrderEntity entity = orderRepository.findById(orderId)
				.orElseThrow(() -> new RuntimeException("order not found"));
		entity.setOrderStatus(status);
		orderRepository.save(entity);
	}

	@Override
	public OrderResponse getOrderById(String id) {
		OrderEntity order = orderRepository.findById(id).orElse(null);
		if (order != null) {
			return convertToOrderResponse(order);
		}
		return null;
	}
}
