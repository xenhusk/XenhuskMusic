# Setup script for Local Music Classification Service
# Run this first to install all dependencies

import subprocess
import sys
import os

def install_package(package):
    """Install a package using pip."""
    try:
        subprocess.check_call([sys.executable, '-m', 'pip', 'install', package])
        print(f"✅ Installed {package}")
        return True
    except subprocess.CalledProcessError:
        print(f"❌ Failed to install {package}")
        return False

def main():
    print("🚀 Setting up Local Music Classification Service...")
    print("=" * 50)
    
    # List of required packages
    packages = [
        'flask>=2.3.0',
        'flask-cors>=4.0.0',
        'librosa>=0.10.0',
        'soundfile>=0.12.0',
        'scikit-learn>=1.3.0',
        'joblib>=1.3.0',
        'pandas>=2.0.0',
        'numpy>=1.24.0'
    ]
    
    print("📦 Installing required packages...")
    print()
    
    success_count = 0
    for package in packages:
        if install_package(package):
            success_count += 1
    
    print()
    print("=" * 50)
    print(f"📊 Installation Summary: {success_count}/{len(packages)} packages installed")
    
    if success_count == len(packages):
        print("🎉 All packages installed successfully!")
        print()
        print("🚀 Next steps:")
        print("   1. Copy your model file to the same directory as the service")
        print("   2. Run: python local_music_classification_service.py")
        print("   3. Use the network URL in your Android app")
    else:
        print("⚠️ Some packages failed to install. Please check the errors above.")
        print("   You may need to install them manually or check your Python environment.")

if __name__ == "__main__":
    main()
