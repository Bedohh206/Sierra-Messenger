from setuptools import setup, find_packages

with open("README.md", "r", encoding="utf-8") as fh:
    long_description = fh.read()

setup(
    name="sierra-messenger",
    version="1.0.0",
    author="Alpha B Kamara",
    description="A lightweight Bluetooth-based peer-to-peer communication platform",
    long_description=long_description,
    long_description_content_type="text/markdown",
    url="https://github.com/Bedohh206/Sierra-Messenger",
    packages=find_packages(),
    classifiers=[
        "Development Status :: 4 - Beta",
        "Intended Audience :: End Users/Desktop",
        "License :: OSI Approved :: MIT License",
        "Operating System :: OS Independent",
        "Programming Language :: Python :: 3",
        "Programming Language :: Python :: 3.8",
        "Programming Language :: Python :: 3.9",
        "Programming Language :: Python :: 3.10",
        "Topic :: Communications :: Chat",
    ],
    python_requires=">=3.8",
    install_requires=[
        "PyBluez>=0.23",
        "Pillow>=10.1.0",
        "cryptography>=41.0.7",
    ],
    entry_points={
        "console_scripts": [
            "sierra-messenger=sierra_messenger.cli:main",
        ],
    },
)
