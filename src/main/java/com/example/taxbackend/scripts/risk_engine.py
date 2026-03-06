#!/usr/bin/env python3
"""
AI Compliance Risk Engine
Zimbabwe Music Tax System - Academic Prototype

Purpose: Detect potential tax evasion behaviors using hybrid ML + Rules approach
Techniques: Isolation Forest, Statistical Analysis, Rule-Based Detection
Input: JSON with artist features
Output: JSON with risk score, level, and explainable indicators

Academic Note: This is a prototype for demonstrating AI-assisted compliance.
Production systems require extensive validation, legal review, and human oversight.
"""

import sys
import json
import numpy as np
import pandas as pd
from sklearn.ensemble import IsolationForest
from sklearn.preprocessing import StandardScaler
from sklearn.cluster import DBSCAN
from collections import defaultdict
from datetime import datetime

# ============================================================================
# CONFIGURATION
# ============================================================================

ISOLATION_FOREST_CONTAMINATION = 0.1  # Expected proportion of outliers
REVENUE_SIMILARITY_THRESHOLD = 0.85   # Cosine similarity threshold
PAYPAL_SHARING_HIGH_RISK = 3          # Number of accounts to trigger alert
THRESHOLD_GAMING_VARIANCE = 0.05      # Revenue variance near threshold

# Risk Level Thresholds
RISK_LOW_MAX = 0.3
RISK_MEDIUM_MAX = 0.6
RISK_HIGH_MIN = 0.7

# ============================================================================
# FEATURE EXTRACTION
# ============================================================================

def extract_features(artist_data):
    """
    Extract numerical features for ML analysis

    Features:
    - Revenue patterns (mean, variance, trend)
    - Tax compliance metrics
    - Behavioral signals
    - Temporal patterns
    """
    features = {}

    # Revenue features
    revenues = artist_data.get('monthlyRevenues', [])
    if revenues:
        features['revenue_mean'] = np.mean(revenues)
        features['revenue_std'] = np.std(revenues)
        features['revenue_variance'] = np.var(revenues)
        features['revenue_cv'] = np.std(revenues) / (np.mean(revenues) + 1e-6)  # Coefficient of variation

        # Revenue trend (linear regression slope)
        if len(revenues) > 1:
            x = np.arange(len(revenues))
            slope = np.polyfit(x, revenues, 1)[0]
            features['revenue_trend'] = slope
        else:
            features['revenue_trend'] = 0.0

        # Threshold gaming detection
        threshold = artist_data.get('taxThreshold', 100.0)
        near_threshold_count = sum(1 for r in revenues if abs(r - threshold) / threshold < THRESHOLD_GAMING_VARIANCE)
        features['near_threshold_ratio'] = near_threshold_count / len(revenues)
    else:
        features['revenue_mean'] = 0.0
        features['revenue_std'] = 0.0
        features['revenue_variance'] = 0.0
        features['revenue_cv'] = 0.0
        features['revenue_trend'] = 0.0
        features['near_threshold_ratio'] = 0.0

    # Tax compliance features
    features['missed_tax_periods'] = artist_data.get('missedTaxPeriods', 0)
    features['tax_payment_ratio'] = artist_data.get('taxPaymentRatio', 1.0)
    features['consecutive_compliant_months'] = artist_data.get('consecutiveCompliantMonths', 0)
    features['total_revenue'] = artist_data.get('totalRevenueToDate', 0.0)
    features['total_tax_paid'] = artist_data.get('totalTaxPaid', 0.0)
    features['outstanding_tax'] = artist_data.get('outstandingTax', 0.0)

    # Behavioral features
    features['recalculation_frequency'] = artist_data.get('recalculationCount', 0)
    features['days_since_registration'] = artist_data.get('daysSinceRegistration', 1)
    features['activity_frequency'] = features['recalculation_frequency'] / (features['days_since_registration'] + 1)

    return features

# ============================================================================
# RULE-BASED RISK DETECTION
# ============================================================================

def detect_rule_based_risks(artist_data, all_artists_data):
    """
    Apply rule-based detection for known evasion patterns
    Returns list of risk indicators and rule-based risk score
    """
    indicators = []
    rule_risk_score = 0.0

    artist_id = artist_data.get('artistId')

    # Rule 1: Shared PayPal/Payment Accounts
    paypal = artist_data.get('paypalEmail')
    if paypal:
        shared_count = sum(1 for a in all_artists_data
                          if a.get('paypalEmail') == paypal and a.get('artistId') != artist_id)
        if shared_count >= PAYPAL_SHARING_HIGH_RISK:
            indicators.append(f"Shared PayPal account with {shared_count} other artists (HIGH RISK)")
            rule_risk_score += 0.4
        elif shared_count >= 2:
            indicators.append(f"Shared PayPal account with {shared_count} other artists")
            rule_risk_score += 0.2

    # Rule 2: Similar Revenue Patterns
    similar_artists = find_similar_revenue_patterns(artist_data, all_artists_data)
    if len(similar_artists) >= 2:
        indicators.append(f"Highly similar revenue patterns to {len(similar_artists)} other artists")
        rule_risk_score += 0.3

    # Rule 3: Threshold Gaming
    revenues = artist_data.get('monthlyRevenues', [])
    threshold = artist_data.get('taxThreshold', 100.0)
    if revenues:
        near_threshold_count = sum(1 for r in revenues if abs(r - threshold) / threshold < THRESHOLD_GAMING_VARIANCE)
        if near_threshold_count >= 3:
            indicators.append(f"Revenue repeatedly near tax threshold ({near_threshold_count} times)")
            rule_risk_score += 0.25

    # Rule 4: High Missed Payments
    missed = artist_data.get('missedTaxPeriods', 0)
    if missed >= 5:
        indicators.append(f"High number of missed tax periods ({missed})")
        rule_risk_score += 0.2
    elif missed >= 3:
        indicators.append(f"Multiple missed tax periods ({missed})")
        rule_risk_score += 0.1

    # Rule 5: Unusual Activity Patterns
    recalc_count = artist_data.get('recalculationCount', 0)
    days = artist_data.get('daysSinceRegistration', 1)
    if days > 30 and recalc_count > days / 10:  # More than 1 recalc per 10 days
        indicators.append(f"Unusually high tax recalculation frequency ({recalc_count} times)")
        rule_risk_score += 0.15

    # Rule 6: Revenue Volatility
    revenues = artist_data.get('monthlyRevenues', [])
    if len(revenues) >= 3:
        cv = np.std(revenues) / (np.mean(revenues) + 1e-6)
        if cv > 2.0:  # Coefficient of variation > 2
            indicators.append(f"Extremely volatile revenue patterns (CV: {cv:.2f})")
            rule_risk_score += 0.15

    # Rule 7: Same Google Account ID Pattern
    google_id = artist_data.get('googleAccountId', '')
    if google_id:
        # Check for artists with very similar Google IDs (potential manipulation)
        similar_google_ids = sum(1 for a in all_artists_data
                                if a.get('googleAccountId', '').startswith(google_id[:10])
                                and a.get('artistId') != artist_id)
        if similar_google_ids >= 2:
            indicators.append(f"Multiple artists with similar Google account patterns")
            rule_risk_score += 0.2

    # Cap rule-based score at 1.0
    rule_risk_score = min(rule_risk_score, 1.0)

    return indicators, rule_risk_score

# ============================================================================
# SIMILARITY DETECTION
# ============================================================================

def find_similar_revenue_patterns(target_artist, all_artists):
    """
    Find artists with similar revenue patterns using cosine similarity
    """
    target_revenues = target_artist.get('monthlyRevenues', [])
    if not target_revenues or len(target_revenues) < 3:
        return []

    similar_artists = []
    target_id = target_artist.get('artistId')

    for artist in all_artists:
        if artist.get('artistId') == target_id:
            continue

        other_revenues = artist.get('monthlyRevenues', [])
        if not other_revenues or len(other_revenues) < 3:
            continue

        # Normalize to same length (take minimum)
        min_len = min(len(target_revenues), len(other_revenues))
        target_vec = np.array(target_revenues[:min_len])
        other_vec = np.array(other_revenues[:min_len])

        # Cosine similarity
        similarity = cosine_similarity(target_vec, other_vec)

        if similarity >= REVENUE_SIMILARITY_THRESHOLD:
            similar_artists.append({
                'artistId': artist.get('artistId'),
                'similarity': similarity
            })

    return similar_artists

def cosine_similarity(vec1, vec2):
    """Calculate cosine similarity between two vectors"""
    dot_product = np.dot(vec1, vec2)
    norm1 = np.linalg.norm(vec1)
    norm2 = np.linalg.norm(vec2)

    if norm1 == 0 or norm2 == 0:
        return 0.0

    return dot_product / (norm1 * norm2)

# ============================================================================
# ML-BASED ANOMALY DETECTION
# ============================================================================

def ml_anomaly_detection(all_artists_data):
    """
    Use Isolation Forest to detect anomalous behavior patterns
    Returns dict mapping artistId to anomaly score
    """
    if len(all_artists_data) < 10:
        # Not enough data for meaningful ML analysis
        return {artist.get('artistId'): 0.0 for artist in all_artists_data}

    # Extract features for all artists
    feature_list = []
    artist_ids = []

    for artist in all_artists_data:
        features = extract_features(artist)
        feature_list.append(list(features.values()))
        artist_ids.append(artist.get('artistId'))

    # Convert to numpy array
    X = np.array(feature_list)

    # Normalize features
    scaler = StandardScaler()
    X_scaled = scaler.fit_transform(X)

    # Train Isolation Forest
    iso_forest = IsolationForest(
        contamination=ISOLATION_FOREST_CONTAMINATION,
        random_state=42,
        n_estimators=100
    )

    # Predict anomalies (-1 = anomaly, 1 = normal)
    predictions = iso_forest.fit_predict(X_scaled)

    # Get anomaly scores (more negative = more anomalous)
    anomaly_scores = iso_forest.score_samples(X_scaled)

    # Normalize scores to 0-1 range (1 = most anomalous)
    min_score = anomaly_scores.min()
    max_score = anomaly_scores.max()

    if max_score - min_score > 0:
        normalized_scores = 1 - (anomaly_scores - min_score) / (max_score - min_score)
    else:
        normalized_scores = np.zeros_like(anomaly_scores)

    # Create mapping
    anomaly_map = {artist_id: float(score)
                   for artist_id, score in zip(artist_ids, normalized_scores)}

    return anomaly_map

# ============================================================================
# RISK SCORE CALCULATION
# ============================================================================

def calculate_risk_score(artist_data, all_artists_data, ml_anomaly_scores):
    """
    Hybrid risk score combining ML and rule-based approaches

    Formula:
    Final Risk = 0.4 * ML_Score + 0.6 * Rule_Score

    Rationale: Rules are more interpretable and reliable for known patterns,
    while ML catches novel anomalies
    """
    artist_id = artist_data.get('artistId')

    # Get ML anomaly score
    ml_score = ml_anomaly_scores.get(artist_id, 0.0)

    # Get rule-based risk
    indicators, rule_score = detect_rule_based_risks(artist_data, all_artists_data)

    # Combine scores (weighted average)
    final_score = 0.4 * ml_score + 0.6 * rule_score

    # Determine risk level
    if final_score >= RISK_HIGH_MIN:
        risk_level = "HIGH"
    elif final_score >= RISK_LOW_MAX:
        risk_level = "MEDIUM"
    else:
        risk_level = "LOW"

    # Add ML-specific indicators if score is high
    if ml_score > 0.7:
        indicators.insert(0, f"ML anomaly detection: Unusual behavior pattern (score: {ml_score:.2f})")

    return {
        'riskScore': round(final_score, 3),
        'riskLevel': risk_level,
        'mlAnomalyScore': round(ml_score, 3),
        'ruleBasedScore': round(rule_score, 3),
        'indicators': indicators if indicators else ["No significant risk indicators detected"]
    }

# ============================================================================
# MAIN EXECUTION
# ============================================================================

def analyze_risk(input_data):
    """
    Main risk analysis function

    Input structure:
    {
        "targetArtist": { artist features },
        "allArtists": [ array of all artists for comparison ]
    }
    """
    try:
        target_artist = input_data.get('targetArtist')
        all_artists = input_data.get('allArtists', [])

        if not target_artist:
            return {
                'error': 'Missing targetArtist in input data',
                'timestamp': datetime.now().isoformat()
            }

        # Run ML anomaly detection on all artists
        ml_scores = ml_anomaly_detection(all_artists)

        # Calculate risk for target artist
        risk_result = calculate_risk_score(target_artist, all_artists, ml_scores)

        # Add metadata
        risk_result['artistId'] = target_artist.get('artistId')
        risk_result['assessedAt'] = datetime.now().isoformat()
        risk_result['analysisVersion'] = '1.0'
        risk_result['artistsAnalyzed'] = len(all_artists)

        return risk_result

    except Exception as e:
        return {
            'error': str(e),
            'timestamp': datetime.now().isoformat()
        }

# ============================================================================
# CLI INTERFACE
# ============================================================================

if __name__ == '__main__':
    try:
        # Read JSON input from stdin
        input_json = sys.stdin.read()
        input_data = json.loads(input_json)

        # Perform risk analysis
        result = analyze_risk(input_data)

        # Output JSON result to stdout
        print(json.dumps(result, indent=2))

        # Exit with success
        sys.exit(0)

    except json.JSONDecodeError as e:
        error_result = {
            'error': f'Invalid JSON input: {str(e)}',
            'timestamp': datetime.now().isoformat()
        }
        print(json.dumps(error_result, indent=2))
        sys.exit(1)

    except Exception as e:
        error_result = {
            'error': f'Analysis failed: {str(e)}',
            'timestamp': datetime.now().isoformat()
        }
        print(json.dumps(error_result, indent=2))
        sys.exit(1)