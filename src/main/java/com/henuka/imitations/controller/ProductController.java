package com.henuka.imitations.controller;

import com.henuka.imitations.model.Product;
import com.henuka.imitations.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.List;

@Controller
@RequestMapping("/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private static final int PAGE_SIZE = 12;

    @GetMapping
    public String listProducts(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) Boolean inStock,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "name") String sort,
            @RequestParam(defaultValue = "asc") String direction,
            Model model) {
        
        PageRequest pageRequest = PageRequest.of(
            page, 
            PAGE_SIZE, 
            Sort.Direction.fromString(direction), 
            sort
        );

        Page<Product> products = productService.searchProducts(
            category, 
            minPrice, 
            maxPrice, 
            inStock, 
            search, 
            pageRequest
        );

        model.addAttribute("products", products);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", products.getTotalPages());
        model.addAttribute("category", category);
        model.addAttribute("minPrice", minPrice);
        model.addAttribute("maxPrice", maxPrice);
        model.addAttribute("inStock", inStock);
        model.addAttribute("search", search);
        model.addAttribute("sort", sort);
        model.addAttribute("direction", direction);

        return "products/list";
    }

    @GetMapping("/{id}")
    public String viewProduct(@PathVariable Long id, Model model) {
        Product product = productService.getProductById(id);
        
        // Get related products
        List<Product> relatedProducts = productService.getRelatedProducts(
            product.getCategory(),
            product.getId(),
            PageRequest.of(0, 4)
        );

        model.addAttribute("product", product);
        model.addAttribute("relatedProducts", relatedProducts);
        
        return "products/view";
    }

    // Admin endpoints
    @GetMapping("/admin/new")
    public String newProductForm(Model model) {
        model.addAttribute("product", new Product());
        return "admin/products/form";
    }

    @PostMapping("/admin")
    public String createProduct(
            @Valid @ModelAttribute Product product,
            BindingResult result,
            RedirectAttributes redirectAttributes) {
        
        if (result.hasErrors()) {
            return "admin/products/form";
        }

        try {
            productService.createProduct(product);
            redirectAttributes.addFlashAttribute("message", "Product created successfully!");
            return "redirect:/products/admin";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/products/admin/new";
        }
    }

    @GetMapping("/admin/{id}/edit")
    public String editProductForm(@PathVariable Long id, Model model) {
        model.addAttribute("product", productService.getProductById(id));
        return "admin/products/form";
    }

    @PostMapping("/admin/{id}")
    public String updateProduct(
            @PathVariable Long id,
            @Valid @ModelAttribute Product product,
            BindingResult result,
            RedirectAttributes redirectAttributes) {
        
        if (result.hasErrors()) {
            return "admin/products/form";
        }

        try {
            productService.updateProduct(id, product);
            redirectAttributes.addFlashAttribute("message", "Product updated successfully!");
            return "redirect:/products/admin";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/products/admin/" + id + "/edit";
        }
    }

    @PostMapping("/admin/{id}/delete")
    public String deleteProduct(
            @PathVariable Long id,
            RedirectAttributes redirectAttributes) {
        try {
            productService.deleteProduct(id);
            redirectAttributes.addFlashAttribute("message", "Product deleted successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/products/admin";
    }

    // API endpoints for AJAX calls
    @GetMapping("/api/featured")
    @ResponseBody
    public ResponseEntity<List<Product>> getFeaturedProducts() {
        return ResponseEntity.ok(productService.getFeaturedProducts());
    }

    @PostMapping("/api/admin/{id}/stock")
    @ResponseBody
    public ResponseEntity<?> updateStock(
            @PathVariable Long id,
            @RequestParam int quantity) {
        try {
            Product product = productService.updateStock(id, quantity);
            return ResponseEntity.ok(product);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/api/check-stock/{id}")
    @ResponseBody
    public ResponseEntity<Boolean> checkStock(
            @PathVariable Long id,
            @RequestParam int quantity) {
        return ResponseEntity.ok(productService.isInStock(id, quantity));
    }
}
