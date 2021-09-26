package me.demo.microservices.composite.product.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import me.demo.api.core.product.Product;
import me.demo.api.core.product.ProductService;
import me.demo.api.core.recommendation.Recommendation;
import me.demo.api.core.recommendation.RecommendationService;
import me.demo.api.core.review.Review;
import me.demo.api.core.review.ReviewService;
import me.demo.util.exceptions.InvalidInputException;
import me.demo.util.exceptions.NotFoundException;
import me.demo.util.http.HttpErrorInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.List;

@Component
public class ProductCompositeIntegration implements ProductService, RecommendationService, ReviewService {

    private static final Logger LOG = LoggerFactory.getLogger(ProductCompositeIntegration.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper mapper;

    private final String productServiceUrl;
    private final String recommendationServiceUrl;
    private final String reviewServiceUrl;

    @Autowired
    public ProductCompositeIntegration(
            RestTemplate restTemplate,
            ObjectMapper mapper,

            @Value("${app.product-service.host}") String productServiceHost,
            @Value("${app.product-service.port}") int    productServicePort,

            @Value("${app.recommendation-service.host}") String recommendationServiceHost,
            @Value("${app.recommendation-service.port}") int    recommendationServicePort,

            @Value("${app.review-service.host}") String reviewServiceHost,
            @Value("${app.review-service.port}") int    reviewServicePort
    ) {

        this.restTemplate = restTemplate;
        this.mapper = mapper;

        productServiceUrl        = "http://" + productServiceHost + ":" + productServicePort + "/product/";
        recommendationServiceUrl = "http://" + recommendationServiceHost + ":" + recommendationServicePort + "/recommendation?productId=";
        reviewServiceUrl         = "http://" + reviewServiceHost + ":" + reviewServicePort + "/review?productId=";
    }

    @Override
    public Product getProduct(int productId) {
        String url = productServiceUrl + productId;
        try{
            Product product = restTemplate.getForObject(url, Product.class);
            return product;
        } catch (HttpClientErrorException ex){
            switch (ex.getStatusCode()){
                case NOT_FOUND:
                    throw new NotFoundException((getErrorMessage(ex)));
                case UNPROCESSABLE_ENTITY:
                    throw new InvalidInputException(getErrorMessage(ex));
                default:
                    LOG.warn("Got a unexpected HTTP error: {}, will rethrow it", ex.getStatusCode());
                    LOG.warn("Error body: {}", ex.getResponseBodyAsString());
                    throw ex;
            }
        }

    }

    @Override
    public List<Recommendation> getRecommendations(int productId) {
        String url = recommendationServiceUrl + productId;
        try {
            List<Recommendation> recommendations = restTemplate.exchange(url, HttpMethod.GET, null, new ParameterizedTypeReference<List<Recommendation>>() {
            }).getBody();
            return recommendations;
        } catch (HttpClientErrorException ex){
            LOG.warn("Got an exception while requesting recommendations, return zero recommendations: {}", ex.getMessage());
            return List.of();
        }
    }

    @Override
    public List<Review> getReviews(int productId) {
        String url = reviewServiceUrl + productId;
        try {
            List<Review> reviews = restTemplate.exchange(url, HttpMethod.GET, null, new ParameterizedTypeReference<List<Review>>() {
            }).getBody();
            return reviews;
        } catch (HttpClientErrorException ex){
            LOG.warn("Got an exception while requesting recommendations, return zero recommendations: {}", ex.getMessage());
            return List.of();
        }
    }

    private String getErrorMessage(HttpClientErrorException ex) {
        try {
            return mapper.readValue(ex.getResponseBodyAsString(), HttpErrorInfo.class).getMessage();
        } catch (IOException ioex) {
            return ex.getMessage();
        }
    }
}
