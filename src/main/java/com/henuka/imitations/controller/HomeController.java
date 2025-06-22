package com.henuka.imitations.controller;

import com.henuka.imitations.model.Product;
import com.henuka.imitations.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class HomeController {

    private final ProductService productService;

    @GetMapping("/")
    public String home(Model model) {
        // Get featured products for the homepage
        List<Product> featuredProducts = productService.getFeaturedProducts();
        model.addAttribute("featuredProducts", featuredProducts);
        
        // Add any necessary data for the hero section
        model.addAttribute("heroTitle", "Welcome to Henuka Imitations");
        model.addAttribute("heroSubtitle", "Discover our exclusive collection");
        
        return "index";
    }

    @GetMapping("/about")
    public String about() {
        return "about";
    }

    @GetMapping("/contact")
    public String contact() {
        return "contact";
    }

    // Chatbot endpoint to handle user queries
    @PostMapping("/api/chat")
    @ResponseBody
    public ResponseEntity<ChatResponse> handleChatMessage(@RequestBody ChatRequest request) {
        // Here you would typically integrate with a chatbot service
        // For now, we'll return some basic responses
        String response = generateChatResponse(request.message());
        return ResponseEntity.ok(new ChatResponse(response));
    }

    private String generateChatResponse(String message) {
        message = message.toLowerCase();
        
        // Basic response logic - in a real application, you'd integrate with a proper chatbot service
        if (message.contains("hello") || message.contains("hi")) {
            return "Hello! How can I help you today?";
        } else if (message.contains("shipping")) {
            return "We offer free shipping on orders above ₹500. Standard shipping cost is ₹50.";
        } else if (message.contains("return") || message.contains("refund")) {
            return "We have a 7-day return policy. Please contact our support team for assistance.";
        } else if (message.contains("payment")) {
            return "We accept all major credit cards, UPI, and net banking.";
        } else if (message.contains("delivery")) {
            return "Standard delivery takes 3-5 business days.";
        } else if (message.contains("contact")) {
            return "You can reach us at support@henukaimitations.com or call us at +91-XXXXXXXXXX.";
        } else if (message.contains("price")) {
            return "Our prices are very competitive. You can check specific product prices on their respective pages.";
        } else if (message.contains("discount") || message.contains("offer")) {
            return "We regularly offer discounts. Sign up for our newsletter to stay updated!";
        } else if (message.contains("track")) {
            return "You can track your order using the order number on our order tracking page.";
        } else {
            return "I'm here to help! Feel free to ask about our products, shipping, returns, or any other questions.";
        }
    }

    // Error handling
    @ExceptionHandler(Exception.class)
    public String handleError(Exception e, Model model) {
        model.addAttribute("error", e.getMessage());
        return "error";
    }

    // Records for chat functionality
    private record ChatRequest(String message) {}
    private record ChatResponse(String message) {}

    // Chatbot initialization endpoint
    @GetMapping("/api/chat/init")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> initializeChat() {
        return ResponseEntity.ok(Map.of(
            "botName", "Henuka Assistant",
            "welcomeMessage", "Hello! I'm your shopping assistant. How can I help you today?",
            "suggestedQueries", List.of(
                "Tell me about shipping",
                "What's your return policy?",
                "How can I track my order?",
                "What payment methods do you accept?"
            )
        ));
    }
}
