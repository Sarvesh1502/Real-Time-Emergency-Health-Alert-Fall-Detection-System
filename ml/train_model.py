import json
import math
import os
from pathlib import Path

import pandas as pd
import numpy as np
from sklearn.linear_model import LogisticRegression
from sklearn.model_selection import train_test_split
from sklearn.metrics import classification_report

# Paths
ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / 'data' / 'sample_sensor.csv'
OUT = ROOT / 'backend' / 'src' / 'main' / 'resources' / 'model' / 'fall_model.bin'

# Load data (if missing, synthesize)
if DATA.exists():
    df = pd.read_csv(DATA)
else:
    # synthesize minimal data
    rng = np.random.default_rng(0)
    n = 400
    normal = pd.DataFrame({
        'ax': rng.normal(0, 0.4, n),
        'ay': rng.normal(0, 0.4, n),
        'az': 9.81 + rng.normal(0, 0.4, n),
        'gx': rng.normal(0, 3, n),
        'gy': rng.normal(0, 3, n),
        'gz': rng.normal(0, 3, n),
        'label': 0
    })
    falls = pd.DataFrame({
        'ax': rng.normal(0, 8, n) + rng.choice([15, -15], n),
        'ay': rng.normal(0, 8, n) + rng.choice([15, -15], n),
        'az': rng.normal(12, 6, n),
        'gx': rng.normal(0, 40, n),
        'gy': rng.normal(0, 40, n),
        'gz': rng.normal(0, 40, n),
        'label': 1
    })
    df = pd.concat([normal, falls], ignore_index=True)

# Features
ax, ay, az = df['ax'].values, df['ay'].values, df['az'].values
gx, gy, gz = df['gx'].values, df['gy'].values, df['gz'].values
accel_mag = np.sqrt(ax*ax + ay*ay + az*az)
gyro_mag = np.sqrt(gx*gx + gy*gy + gz*gz)

X = np.stack([accel_mag, gyro_mag], axis=1)
if 'label' in df.columns:
    y = df['label'].astype(int).values
else:
    # Create a label using percentile to ensure some positives
    pct = 90
    thr = np.percentile(accel_mag, pct)
    y = (accel_mag > thr).astype(int)
    # If too few positives, relax threshold
    if y.sum() < 5:
        pct = 80
        thr = np.percentile(accel_mag, pct)
        y = (accel_mag > thr).astype(int)

unique, counts = np.unique(y, return_counts=True)
do_stratify = (len(unique) == 2 and counts.min() >= 2)
X_train, X_test, y_train, y_test = train_test_split(
    X, y, test_size=0.25, random_state=42, stratify=y if do_stratify else None
)

clf = LogisticRegression(max_iter=500)
clf.fit(X_train, y_train)

y_pred = clf.predict(X_test)
print(classification_report(y_test, y_pred))

# Export weights to a simple JSON model
w = clf.coef_[0].tolist()  # [w_accel_mag, w_gyro_mag]
b = float(clf.intercept_[0])
model = {
    'type': 'logistic_regression',
    'features': ['accel_mag', 'gyro_mag'],
    'weights': w,
    'bias': b,
    'threshold': 0.6
}

OUT.parent.mkdir(parents=True, exist_ok=True)
with open(OUT, 'w', encoding='utf-8') as f:
    json.dump(model, f)

print(f"Saved model to {OUT}")
