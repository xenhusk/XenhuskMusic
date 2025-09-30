# 🚀 Render.com Deployment Guide

Complete step-by-step instructions for deploying the Music Classification Cloud Service to Render.com.

## 📋 Prerequisites

- Render.com account (free tier available)
- Git repository with the cloud service code
- Trained model file from `christian_music_classifier` directory
- Basic understanding of web services

## 🔧 Step 1: Prepare Your Repository

### 1.1 Create Service Directory Structure
```
your-repo/
├── cloud_classification_service.py
├── requirements.txt
├── render.yaml
├── models/
│   └── improved_audio_classifier_random_forest.joblib
└── README.md
```

### 1.2 Copy Your Trained Model
```bash
# Copy the trained model from your working directory
cp "C:\Users\xenhu\OneDrive\Documents\GitHub\christian_music_classifier\models\improved_audio_classifier_random_forest.joblib" ./models/
```

### 1.3 Verify Files Are Present
Make sure these files exist in your repository root:
- ✅ `cloud_classification_service.py`
- ✅ `requirements.txt`
- ✅ `render.yaml`
- ✅ `models/improved_audio_classifier_random_forest.joblib`

## 🌐 Step 2: Deploy to Render.com

### 2.1 Create Render Account
1. Go to [render.com](https://render.com)
2. Sign up with GitHub (recommended)
3. Verify your email address

### 2.2 Connect Your Repository
1. Click **"New +"** in the Render dashboard
2. Select **"Web Service"**
3. Connect your GitHub account if not already connected
4. Select your repository containing the cloud service

### 2.3 Configure the Web Service

#### Basic Settings
- **Name**: `music-classification-service` (or your preferred name)
- **Environment**: `Python 3`
- **Region**: Choose closest to your users
- **Branch**: `main` (or your default branch)

#### Build & Deploy Settings
- **Build Command**: `pip install -r requirements.txt`
- **Start Command**: `python cloud_classification_service.py`
- **Python Version**: `3.11` (or latest available)

#### Instance Configuration
- **Instance Type**: 
  - **Free Tier**: Starter (512MB RAM) - Good for testing
  - **Paid Tier**: Standard (1GB RAM) - Recommended for production
- **Auto-Deploy**: `Yes` (deploys automatically on git push)

### 2.4 Environment Variables (Optional)
Add these in the Render dashboard under "Environment":

| Variable | Value | Description |
|----------|-------|-------------|
| `PORT` | `10000` | Port number (auto-set by Render) |
| `LOG_LEVEL` | `INFO` | Logging level |
| `MODEL_PATH` | `models/improved_audio_classifier_random_forest.joblib` | Custom model path |

### 2.5 Deploy
1. Click **"Create Web Service"**
2. Render will automatically:
   - Clone your repository
   - Install dependencies from `requirements.txt`
   - Start the Flask service
   - Provide a public URL

## 🔍 Step 3: Verify Deployment

### 3.1 Check Service Health
Once deployed, test your service:

```bash
# Replace YOUR_SERVICE_URL with your actual Render URL
curl https://your-service-name.onrender.com/health
```

Expected response:
```json
{
    "status": "healthy",
    "model_loaded": true,
    "service": "music-classification-service",
    "version": "1.0.0"
}
```

### 3.2 Test Classification Endpoint
```bash
# Test with sample audio data
curl -X POST https://your-service-name.onrender.com/classify \
  -H "Content-Type: application/json" \
  -d '{
    "audio_data": [0.1, 0.2, 0.3, 0.4, 0.5],
    "sample_rate": 22050,
    "song_id": "test_song"
  }'
```

### 3.3 Check Logs
1. Go to your service dashboard on Render
2. Click **"Logs"** tab
3. Look for:
   - ✅ "Model loaded successfully!"
   - ✅ "Starting Music Classification Service on port 10000"
   - ✅ "Available endpoints:" message

## 📱 Step 4: Update Android App

### 4.1 Update Service URL
In your Android project, update `CloudClassificationService.kt`:

```kotlin
class CloudClassificationService(
    private val client: HttpClient,
    private val baseUrl: String = "https://your-service-name.onrender.com" // Replace with your actual URL
) {
```

### 4.2 Test Android Integration
1. Build and run your Android app
2. Navigate to Music Classification screen
3. Tap "Check Health" button
4. Verify connection to your Render service

## 🔧 Step 5: Configuration & Optimization

### 5.1 Render.com Settings

#### Free Tier Limitations
- **Sleep Mode**: Service sleeps after 15 minutes of inactivity
- **Cold Start**: First request after sleep takes ~30 seconds
- **Bandwidth**: 100GB/month
- **Build Time**: 90 minutes/month

#### Paid Tier Benefits
- **Always On**: No sleep mode
- **Faster Cold Starts**: ~5 seconds
- **More Resources**: 1GB+ RAM
- **Custom Domains**: Use your own domain

### 5.2 Performance Optimization

#### Enable Gzip Compression
Add to your Flask app:
```python
from flask_compress import Compress

app = Flask(__name__)
Compress(app)
```

#### Add Caching Headers
```python
@app.after_request
def after_request(response):
    response.headers.add('Cache-Control', 'public, max-age=300')
    return response
```

### 5.3 Monitoring & Alerts

#### Set Up Health Checks
1. Go to your service dashboard
2. Click **"Settings"**
3. Enable **"Health Check"**
4. Set path to `/health`

#### Monitor Performance
- Check **"Metrics"** tab for CPU/Memory usage
- Monitor **"Logs"** for errors
- Set up **"Alerts"** for downtime

## 🐛 Troubleshooting

### Common Issues

#### 1. Model Not Found
**Error**: `Trained model not found in any expected location`

**Solution**:
```bash
# Verify model file exists
ls -la models/improved_audio_classifier_random_forest.joblib

# Check file permissions
chmod 644 models/improved_audio_classifier_random_forest.joblib
```

#### 2. Build Failures
**Error**: `pip install failed`

**Solution**:
- Check `requirements.txt` syntax
- Verify Python version compatibility
- Review build logs for specific errors

#### 3. Service Timeout
**Error**: `Request timeout`

**Solution**:
- Upgrade to paid tier for better performance
- Optimize model loading
- Implement request queuing

#### 4. Memory Issues
**Error**: `Out of memory`

**Solution**:
- Upgrade to larger instance
- Optimize model size
- Implement model caching

### Debug Steps

1. **Check Build Logs**:
   ```
   Render Dashboard → Your Service → Logs → Build Logs
   ```

2. **Check Runtime Logs**:
   ```
   Render Dashboard → Your Service → Logs → Runtime Logs
   ```

3. **Test Endpoints**:
   ```bash
   # Health check
   curl https://your-service.onrender.com/health
   
   # Model info
   curl https://your-service.onrender.com/model_info
   ```

4. **Verify Model Loading**:
   Look for these log messages:
   - ✅ "Loading model from: models/improved_audio_classifier_random_forest.joblib"
   - ✅ "Model loaded successfully!"
   - ✅ "Features: X → Y (selected)"

## 📊 Monitoring Your Service

### Render Dashboard Metrics
- **CPU Usage**: Monitor processing load
- **Memory Usage**: Watch for memory leaks
- **Response Time**: Track API performance
- **Error Rate**: Monitor service reliability

### Custom Monitoring
Add logging to track usage:
```python
import logging

@app.route('/classify', methods=['POST'])
def classify_single():
    logger.info(f"Classification request received")
    # ... existing code ...
    logger.info(f"Classification completed in {processing_time:.2f}s")
```

## 🔄 Updates & Maintenance

### Deploying Updates
1. Push changes to your Git repository
2. Render automatically detects changes
3. Service rebuilds and redeploys
4. Monitor logs for successful deployment

### Model Updates
1. Replace model file in repository
2. Update model version in code
3. Deploy new version
4. Test with sample data

### Scaling
- **Horizontal**: Add more instances
- **Vertical**: Upgrade instance size
- **Caching**: Implement Redis for model caching
- **CDN**: Use CloudFlare for static assets

## 💰 Cost Estimation

### Free Tier
- **Cost**: $0/month
- **Limitations**: Sleep mode, limited resources
- **Best For**: Testing, development, low traffic

### Paid Tier
- **Cost**: $7-25/month (depending on instance size)
- **Benefits**: Always on, better performance
- **Best For**: Production, high traffic

## 🎯 Production Checklist

Before going live:

- [ ] Service health check returns 200 OK
- [ ] Model loads successfully
- [ ] Classification endpoint works
- [ ] Android app connects successfully
- [ ] Error handling works properly
- [ ] Logging is configured
- [ ] Monitoring is set up
- [ ] Backup strategy is in place
- [ ] Domain is configured (if using custom domain)
- [ ] SSL certificate is valid

## 📞 Support

### Render.com Support
- **Documentation**: [render.com/docs](https://render.com/docs)
- **Community**: [render.com/community](https://render.com/community)
- **Status Page**: [status.render.com](https://status.render.com)

### Service-Specific Issues
- Check service logs first
- Review this deployment guide
- Test endpoints individually
- Verify model file integrity

---

**🎉 Congratulations!** Your music classification service is now live on Render.com and ready to classify music for your Android app!
