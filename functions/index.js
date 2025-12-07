/* eslint-disable max-len */

const httpsFunctions = require("firebase-functions/v2/https");
const functionsLogger = require("firebase-functions/logger");
const {defineSecret} = require("firebase-functions/params");
const sgMail = require("@sendgrid/mail");

const SENDGRID_API_KEY = defineSecret("SENDGRID_API_KEY");

/**
 * Generate a personalized, verbose subject line for verification emails.
 * @param {string} userName Display name for the recipient.
 * @return {string} Subject line to use.
 */
function getPersonalizedSubject(userName) {
  const subjects = [
    `Hello ${userName}, this is your verification code for registration`,
    `Hi ${userName}, verify your LiArm Store account`,
    `${userName}, complete your LiArm Store registration`,
    `Welcome ${userName}! Verify your email address`,
    `${userName}, your LiArm Store verification code`,
    `Hello ${userName}, please verify your account`,
    `${userName}, action required: verify your email`,
    `Hi ${userName}, your registration verification code`,
    `${userName}, verify your LiArm Store email`,
    `Hello ${userName}, complete your account setup`,
  ];
  const randomIndex = Math.floor(Math.random() * subjects.length);
  return subjects[randomIndex];
}

/**
 * HTTPS function that sends a verification email via SendGrid.
 * Expects JSON body: { email: string, code: string, name?: string }.
 *
 * @param {import("firebase-functions/v2/https").Request} req
 * @param {import("firebase-functions/v2/https").Response} res
 * @return {Promise<void>}
 */
exports.sendVerificationEmail = httpsFunctions.onRequest(
    {cors: true, secrets: [SENDGRID_API_KEY]},
    async (req, res) => {
      if (req.method !== "POST") {
        return res.status(405).send("Only POST allowed");
      }

      const {email, code, name} = req.body || {};
      if (!email || !code) {
        return res.status(400).send("Missing email or code");
      }

      // Use name if provided, otherwise use email username part
      const userName = name || email.split("@")[0] || "User";

      try {
        sgMail.setApiKey(SENDGRID_API_KEY.value());

        // HTML email with better formatting to avoid spam
        const htmlContent = `
<!DOCTYPE html>
<html>
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>LiArm Store Verification</title>
</head>
<body style="margin: 0; padding: 0; font-family: Arial, sans-serif; background-color: #f4f4f4;">
  <table role="presentation" style="width: 100%; border-collapse: collapse;">
    <tr>
      <td style="padding: 20px 0; text-align: center; background-color: #1a1a1a;">
        <h1 style="color: #ffffff; margin: 0; font-size: 24px;">LiArm Store</h1>
      </td>
    </tr>
    <tr>
      <td style="padding: 40px 20px; background-color: #ffffff;">
        <table role="presentation" style="width: 100%; max-width: 600px; margin: 0 auto;">
          <tr>
            <td>
              <h2 style="color: #333333; margin: 0 0 20px 0; font-size: 20px;">Email Verification</h2>
              <p style="color: #666666; font-size: 16px; line-height: 1.5; margin: 0 0 20px 0;">
                Hello ${userName}, thank you for registering with LiArm Store. Please use the verification code below to complete your registration:
              </p>
              <div style="background-color: #f8f8f8; border: 2px solid #8b0000; border-radius: 8px; padding: 20px; text-align: center; margin: 30px 0;">
                <p style="color: #333333; font-size: 14px; margin: 0 0 10px 0; font-weight: bold;">Your verification code:</p>
                <p style="color: #8b0000; font-size: 32px; font-weight: bold; letter-spacing: 4px; margin: 0; font-family: 'Courier New', monospace;">${code}</p>
              </div>
              <p style="color: #666666; font-size: 14px; line-height: 1.5; margin: 20px 0 0 0;">
                This code will expire in 24 hours. If you didn't request this code, please ignore this email.
              </p>
            </td>
          </tr>
        </table>
      </td>
    </tr>
    <tr>
      <td style="padding: 20px; text-align: center; background-color: #f4f4f4;">
        <p style="color: #999999; font-size: 12px; margin: 0;">
          © ${new Date().getFullYear()} LiArm Store. All rights reserved.
        </p>
      </td>
    </tr>
  </table>
</body>
</html>
        `;

        // Plain text version for email clients that don't support HTML
        const textContent = `
LiArm Store - Email Verification

Hello ${userName}, thank you for registering with LiArm Store.

Your verification code is: ${code}

This code will expire in 24 hours. If you didn't request this code, please ignore this email.

© ${new Date().getFullYear()} LiArm Store. All rights reserved.
        `;

        const msg = {
          to: email,
          from: {
            email: "jora.abovyanc@gmail.com",
            name: "LiArm Store",
          },
          replyTo: "jora.abovyanc@gmail.com",
          // Personalized subject to avoid spam detection
          subject: getPersonalizedSubject(userName),
          text: textContent.trim(),
          html: htmlContent.trim(),
          // Add categories for better deliverability
          categories: ["verification"],
          // Custom headers to improve deliverability and avoid spam
          headers: {
            "X-Entity-Ref-ID": `verify-${Date.now()}`,
            "List-Unsubscribe": "<mailto:jora.abovyanc@gmail.com?subject=unsubscribe>",
            "List-Unsubscribe-Post": "List-Unsubscribe=One-Click",
            "X-Mailer": "LiArm Store",
            "Precedence": "bulk",
          },
          // Mail settings for better deliverability
          mailSettings: {
            clickTracking: {
              enable: false, // Disable click tracking to avoid spam filters
            },
            openTracking: {
              enable: false, // Disable open tracking
            },
          },
        };

        await sgMail.send(msg);
        functionsLogger.info(`Verification email sent to ${email}`);
        return res.status(200).send("OK");
      } catch (err) {
        functionsLogger.error("SendGrid error:", err);
        return res.status(500).send("Failed to send email");
      }
    },
);
